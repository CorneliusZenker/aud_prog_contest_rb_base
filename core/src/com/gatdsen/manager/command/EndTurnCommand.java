package com.gatdsen.manager.command;

import com.gatdsen.simulation.PlayerController;
import com.gatdsen.simulation.action.ActionLog;

/**
 * Dieser Befehl markiert das Ende eines Zuges und bricht die Befehlsausführung für den aktuellen Spieler im aktuellen
 * Zug ab.
 * Sollte NICHT direkt über den {@link com.gatdsen.manager.Controller} verfügbar sein.
 */
public class EndTurnCommand extends Command {

    /**
     * Erstellt einen neuen Befehl, der das Ende des aktuellen Zuges markiert.
     */
    public EndTurnCommand() {
    }

    @Override
    public ActionLog onExecute(PlayerController controller) {
        return null;
    }

    @Override
    public boolean endsTurn() {
        return true;
    }
}
