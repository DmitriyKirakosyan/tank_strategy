import model.*;

import java.awt.geom.Point2D;

public final class MyStrategy implements Strategy {

    private int moveNumber = 0;

    @Override
    public void move(Tank self, World world, Move move)
    {

        //System.out.println("move" + (++moveNumber));

       // new HeavyStrategy().perform(self, world, move);
       new AttackPriorityStrategy().perform(self, world, move);
     }

    @Override
    public TankType selectTank(int tankIndex, int teamSize) {
        //return TankType.HEAVY;
        return TankType.MEDIUM;
        //return TankType.TANK_DESTROYER;
    }



}
