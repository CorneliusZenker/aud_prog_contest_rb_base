package com.gatdsen.manager;

import com.gatdsen.manager.command.Command;
import com.gatdsen.manager.player.Bot;
import com.gatdsen.manager.player.Player;
import com.gatdsen.manager.player.PlayerHandler;
import com.gatdsen.networking.ProcessPlayerHandler;
import com.gatdsen.simulation.GameCharacterController;
import com.gatdsen.simulation.GameState;
import com.gatdsen.simulation.Simulation;
import com.gatdsen.simulation.action.ActionLog;
import com.gatdsen.simulation.campaign.CampaignResources;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class Game extends Executable {

    protected final Object schedulingLock = new Object();

    private static final boolean isDebug;

    static {
        isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp");
        if (isDebug) System.err.println("Warning: Debugger engaged; Disabling Bot-Timeout!");
    }

    private GameResults gameResults;
    private Simulation simulation;
    private GameState state;
    private PlayerHandler[] playerHandlers;

    private float[] scores;

    private static final AtomicInteger gameNumber = new AtomicInteger(0);

    private Thread simulationThread;

    protected Game(GameConfig config) {
        super(config);
        if (config.gameMode == GameState.GameMode.Campaign) {
            if (config.players.size() != 1) {
                System.err.println("Campaign only accepts exactly 1 player");
                setStatus(Status.ABORTED);
            }
            config.players.addAll(CampaignResources.getEnemies(config.mapName));
            config.teamCount = config.players.size();
        }
        gameResults = new GameResults(config);
        gameResults.setStatus(getStatus());
    }

    private void create() {

        simulation = new Simulation(config.gameMode, config.mapName, config.teamCount);
        state = simulation.getState();
        if (saveReplay)
            gameResults.setInitialState(state);

        long seed = Manager.getSeed();
        playerHandlers = new PlayerHandler[config.teamCount];
        for (int i = 0; i < config.teamCount; i++) {
            PlayerHandler handler;
            Class<? extends Player> playerClass = config.players.get(i);
            if (Bot.class.isAssignableFrom(playerClass)) {
                handler = new ProcessPlayerHandler(playerClass, gameNumber.get(), i);
            } else {
                if (!gui) {
                    throw new RuntimeException("HumanPlayers can't be used without GUI to capture inputs");
                }
                handler = new LocalPlayerHandler(playerClass);
            }
            GameCharacterController gcController = simulation.getController();
            playerHandlers[i] = handler;
            Future<?> future = handler.init(
                    state,
                    isDebug,
                    seed,
                    (Command command) -> {
                        // Contains action produced by the commands execution
                        command.run(gcController);
                        // TODO
                    }
            );
            try {
                future.get();
            } catch (InterruptedException|ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        gameResults.setPlayerNames(getPlayerNames());
        config = null;
    }

    public void start() {
        synchronized (schedulingLock) {
            if (getStatus() == Status.ABORTED) return;
            setStatus(Status.ACTIVE);
            gameNumber.getAndIncrement();
            create();
            //Init the Log Processor
            if (gui) animationLogProcessor.init(state.copy(), getPlayerNames(), new String[][]{});
            //Run the Game
            simulationThread = new Thread(this::run);
            simulationThread.setName("Game_Simulation_Thread");
            simulationThread.setUncaughtExceptionHandler(this::crashHandler);
            simulationThread.start();
        }
    }

    @Override
    protected void setStatus(Status newStatus) {
        super.setStatus(newStatus);
        if (gameResults!= null) gameResults.setStatus(newStatus);
    }

    /**
     * @return The state of the underlying simulation
     */
    public GameState getState() {
        return state;
    }

    /**
     * Controls Player Execution
     */
    private void run() {
        Thread.currentThread().setName("Game_Thread_" + gameNumber.get());
        while (!pendingShutdown && state.isActive()) {
            synchronized (schedulingLock) {
                if (getStatus() == Status.PAUSED)
                    try {
                        schedulingLock.wait();

                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
            }

            ActionLog firstLog = simulation.clearAndReturnActionLog();
            if (saveReplay)
                gameResults.addActionLog(firstLog);
            if (gui) {
                animationLogProcessor.animate(firstLog);
            }

            GameCharacterController gcController = simulation.getController();
            int currentPlayerIndex = gcController.getTeam();

            // TODO: executor.waitForCompletion();
            PlayerHandler playerHandler = playerHandlers[currentPlayerIndex];
            Future<?> future = playerHandler.executeTurn(
                    state,
                    (Command command) -> {
                        // Contains action produced by the commands execution
                        ActionLog log = command.run(gcController);
                        if (log == null) {
                            return;
                        }
                        if (saveReplay) {
                            gameResults.addActionLog(log);
                        }
                        if (gui) {
                            animationLogProcessor.animate(log);
                            // ToDo: discuss synchronisation for human players
                            // animationLogProcessor.awaitNotification();
                        }
                        if (!command.endsTurn()) {
                            return;
                        }
                        //Contains actions produced by ending the turn (after last command is executed)
                        ActionLog finalLog = simulation.endTurn();
                        if (saveReplay) {
                            gameResults.addActionLog(finalLog);
                        }
                        if (gui) {
                            animationLogProcessor.animate(finalLog);
                            animationLogProcessor.awaitNotification();
                        }
                    }
            );
            try {
                future.get();
            } catch (InterruptedException|ExecutionException e) {
                throw new RuntimeException(e);
            }

            // TODO: futureExecutor.start();
            ActionLog log = simulation.clearAndReturnActionLog();
            if (saveReplay) {
                gameResults.addActionLog(log);
            }
            if (gui && playerHandler.isHumanPlayer()) {
                //Contains Action produced by entering new turn
                animationLogProcessor.animate(log);
            }
        }
        scores = state.getScores();
        setStatus(Status.COMPLETED);
        for (CompletionHandler<Executable> completionListener : completionListeners) {
            completionListener.onComplete(this);
        }
    }

@Override
    public void dispose() {
        //Shutdown all running threads
        super.dispose();
        if (simulationThread != null) {
            simulationThread.interrupt();
        }
        if (state!=null) scores = state.getScores();
        simulation = null;
        state = null;
        simulationThread = null;
        gameResults = null;
    }

    protected String[] getPlayerNames() {
        // TODO
        /*String[] names = new String[players.length];
        int i = 0;
        for (Player p : players) {
            names[i] = p.getName();
            i++;
        }
        return names;*/
        return new String[]{"Player 1", "Player 2"};
    }

    public float[] getScores() {
        return scores;
    }

    public boolean shouldSaveReplay() {
        return super.saveReplay;
    }

    public GameResults getGameResults() {
        return gameResults;
    }

    @Override
    public String toString() {
        return "Game{" +
                "status=" + getStatus() +
                ", completionListeners=" + super.completionListeners +
                ", inputGenerator=" + inputGenerator +
                ", animationLogProcessor=" + animationLogProcessor +
                ", gui=" + gui +
                ", gameResults=" + gameResults +
                ", simulation=" + simulation +
                ", state=" + state +
                /*", players=" + Arrays.toString(players) +*/
                ", simulationThread=" + simulationThread +
                ", uiMessenger=" + uiMessenger +
                ", pendingShutdown=" + pendingShutdown +
                ", config=" + config +
                '}';
    }
}