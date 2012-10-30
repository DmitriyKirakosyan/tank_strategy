import model.*;

public final class MyStrategy implements Strategy {
    private final int ABOUT_WALL_LENGTH = 50;

    @Override
    public void move(Tank self, World world, Move move) {

        if (!this.isTankAboutWall(self, world.getHeight(), world.getWidth()))
        {
            move.setLeftTrackPower(-1.0d);
            move.setRightTrackPower(-1.0d);
        }
        else
        {
            move.setLeftTrackPower(0d);
            move.setRightTrackPower(0d);
        }

        Player targetPlayer = this.chooseTargetPlayer(world.getPlayers(), self.getPlayerName());


        Tank targetTank = this.findTankByPlayerName(targetPlayer.getName(), world.getTanks());

        if (targetTank.getCrewHealth() == 0 || targetTank.getHullDurability() == 0)
        {
            targetTank = this.getAliveTank(world.getTanks(), self);
        }

        if (targetTank != null)
        {
            move.setTurretTurn(self.getTurretAngleTo(targetTank));
        }
        move.setFireType(FireType.PREMIUM_PREFERRED);
    }

    @Override
    public TankType selectTank(int tankIndex, int teamSize) {
        return TankType.HEAVY;
    }

    /* strategy functions */

    /**
     * Возвращает true если танк подобрался на ABOUT_WALL_LENGTH величину к любой из 4х стен
     * @param tank
     * @param wHeight
     * @param wWeight
     * @return
     */
    private boolean isTankAboutWall(Tank tank, double wHeight, double wWeight)
    {
        return tank.getX() < ABOUT_WALL_LENGTH || tank.getY() < ABOUT_WALL_LENGTH ||
               tank.getX() > wWeight - ABOUT_WALL_LENGTH || tank.getY() > wHeight - ABOUT_WALL_LENGTH;
    }

    /**
     * Выбирает игрока из списка игроков кроме себя, по танку которого следует открыть огонь
     * @param players
     * @param selfPlayerName
     * @return
     */
    private Player chooseTargetPlayer(Player[] players, String selfPlayerName)
    {
        Player result = players[0];

        for (int i = 1; i < players.length - 1; ++i)
        {
            if (result.getName().equals(selfPlayerName) || result.getScore() < players[i].getScore())
            {
                if (!players[i].getName().equals(selfPlayerName))
                {
                    result = players[i];
                }
            }
        }
        return result;
    }

    /**
     * Возвращает первый в списке танк, имя игрока которого соответсвует переданному имени игрока
     * @param playerName
     * @param tanks
     * @return
     */
    private Tank findTankByPlayerName(String playerName, Tank[] tanks)
    {
        for (Tank tank : tanks)
        {
            if (tank.getPlayerName().equals(playerName))
            {
                return tank;
            }
        }
        return null;
    }

    /**
     * Возвращает первый в списке живой танк, кроме своего
     * @param tanks
     * @return
     */
    private Tank getAliveTank(Tank[] tanks, Tank self)
    {
        for (Tank tank : tanks)
        {
            if (tank != self && tank.getCrewHealth() != 0 && tank.getHullDurability() != 0)
            {
                return tank;
            }
        }
        return null;
    }

}
