package bots;

import com.gatdsen.manager.player.Bot;
import com.gatdsen.manager.Controller;
import com.gatdsen.simulation.GameState;

public class CampaignTestBot extends Bot {
    @Override
    public String getStudentName() {
        return "Cornelius Zenker";
    }

    @Override
    public int getMatrikel() {
        return -1; //Heh, you thought
    }

    @Override
    public String getName() {
        return "Training Bot";
    }

    @Override
    public void init(GameState state) {

    }

    @Override
    public void executeTurn(GameState state, Controller controller) {
        controller.foo(1);
    }
}
