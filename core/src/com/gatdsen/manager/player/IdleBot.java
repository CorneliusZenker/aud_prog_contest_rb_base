package com.gatdsen.manager.player;

import com.gatdsen.manager.Controller;
import com.gatdsen.manager.StaticGameState;

import java.util.Random;

public class IdleBot extends Bot{
    @Override
    public String getName() {
        return "IdleBot";
    }

    @Override
    public int getMatrikel() {
        return 133769;
    }

    @Override
    public String getStudentName() {
        return "Santa";
    }

    private static Random random;

    @Override
    public void init(StaticGameState state) {
        long seed = 420L;
        random = new Random(seed);
    }

    @Override
    public void executeTurn(StaticGameState state, Controller controller) {

    }


}