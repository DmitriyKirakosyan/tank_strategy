import model.*;

/**
 * User: dmitriy
 */
public class AttackPriorityStrategy implements IStrategyPerformer {

    private final double BONUS_ANGLE = Math.PI / 8;

    private final double ENEMY_CRITICAL_HEALTH = 40;

    private final double MIN_ANGLE = Math.PI / 180;
    private final double MIN_ANGLE_FOR_MOVE = Math.PI / 10;

    @Override
    public void perform(Tank self, World world, Move move)
    {
        //move tank
        boolean moveOk = this.tryMoveForBonus(self, world, move);

        //attack
        double shootAngle = this.shoot(self, world, move, !moveOk);

        if (!moveOk && shootAngle < MIN_ANGLE * 3)
        {
            move.setRightTrackPower(-1);
            move.setLeftTrackPower(-1);
        }

    }

    /* strategy functions */

    /**
     * Управляет пушкой
     * @param self
     * @param world
     * @param move
     * @return возвращает угол, на который поворачивается пушка в данный момент
     */
    private double shoot(Tank self, World world, Move move, boolean needRotateTank)
    {
        Tank tank = this.getMinHealthTank(world.getTanks());
        if (tank == null)
        {
            tank = this.getClosestTank(self, world.getTanks());
        }

        if (tank != null)
        {
            double turretAngleToTank = self.getTurretAngleTo(tank);
            move.setTurretTurn(turretAngleToTank);

            if (needRotateTank) { this.rotateTankToAngle(turretAngleToTank, move); }

            if (turretAngleToTank < MIN_ANGLE)
            {
                move.setFireType(FireType.PREMIUM_PREFERRED);
            }

            return turretAngleToTank;
        }
        return 0;
    }

    private void rotateTankToAngle(double angle, Move move)
    {
        if (angle > 0)
        {
            move.setLeftTrackPower(0.75);
            move.setRightTrackPower(-1);
        }
        else if (angle < 0)
        {
            move.setLeftTrackPower(-1);
            move.setRightTrackPower(0.75);
        }
        else
        {
            //move.setLeftTrackPower(0);
            //move.setRightTrackPower(0);
        }
    }

    /**
     * Пытается найти нужный бонус и отправиться к нему
     * @param self
     * @param world
     * @param move
     * @return возвращает true если движение состоялось
     */
    private boolean tryMoveForBonus(Tank self, World world, Move move)
    {
        Bonus resultBonus = null;
        for (Bonus bonus : world.getBonuses())
        {
            if (Math.abs(self.getAngleTo(bonus)) < BONUS_ANGLE ||
                Math.abs(self.getAngleTo(bonus)) > Math.PI - BONUS_ANGLE)
            {
                if (resultBonus == null || self.getDistanceTo(resultBonus) > self.getDistanceTo(bonus))
                {
                    resultBonus = bonus;
                }
            }
        }

        if (resultBonus != null)
        {
            //System.out.println("angle to bonus " + self.getAngleTo(resultBonus));
            this.moveToPoint(self, move, resultBonus.getX(), resultBonus.getY());
            return true;
        }

        return false;
    }

    private boolean isUsefulBonus(Tank self, Bonus bonus)
    {
        if ((bonus.getType() != BonusType.MEDIKIT ||
                self.getCrewHealth() < self.getCrewMaxHealth() - self.getCrewMaxHealth() / 4) &&
                (bonus.getType() != BonusType.REPAIR_KIT ||
                        self.getHullDurability() < self.getHullMaxDurability() - self.getHullMaxDurability() / 4))
        {
            return true;
        }
        return false;
    }

    private Tank getMinHealthTank(Tank[] tanks)
    {
        Tank result = null;
        for (Tank tank : tanks)
        {
            if (this.isActualEnemyTank(tank))
            {
                if (tank.getHullDurability() <= ENEMY_CRITICAL_HEALTH / 2)
                {
                    result = tank;
                    break;
                }
                if (result == null && (tank.getHullDurability() <= ENEMY_CRITICAL_HEALTH))
                {
                    result = tank;
                }
            }
        }
        return result;
    }

    private Tank getClosestTank(Tank self, Tank[] tanks)
    {
        Tank result = null;
        double previousDistance = 10e10;
        double currentDistance;
        for (Tank tank : tanks)
        {
            if (this.isActualEnemyTank(tank))
            {
                currentDistance = self.getDistanceTo(tank);
                if (result == null || currentDistance < previousDistance)
                {
                    previousDistance = currentDistance;
                    result = tank;
                }
            }
        }
        return result;
    }

    private boolean isActualEnemyTank(Tank tank)
    {
        return !tank.isTeammate() && tank.getCrewHealth() > 0 && tank.getHullDurability() > 0;
    }

    /**
     * Двигает танк в сторону точки
     * @param self
     * @param move
     * @param x
     * @param y
     */
    private void moveToPoint(Tank self, Move move, double x, double y)
    {
        double angleToPoint = self.getAngleTo(x, y);


        if (Math.abs(angleToPoint) < Math.PI / 2)
        {
            if (angleToPoint > MIN_ANGLE_FOR_MOVE)
            {
                move.setLeftTrackPower(0.75);
                move.setRightTrackPower(-1);
            }
            else if (angleToPoint < -MIN_ANGLE_FOR_MOVE)
            {
                move.setLeftTrackPower(-1);
                move.setRightTrackPower(0.75);
            }
            else
            {
                move.setRightTrackPower(1);
                move.setLeftTrackPower(1);
            }
        }
        else
        {
            if (angleToPoint > 0 && angleToPoint < Math.PI - MIN_ANGLE_FOR_MOVE)
            {
                move.setLeftTrackPower(-1);
                move.setRightTrackPower(0.75);
            }
            else if (angleToPoint < 0 && angleToPoint > -Math.PI - MIN_ANGLE_FOR_MOVE)
            {
                move.setLeftTrackPower(0.75);
                move.setRightTrackPower(-1);
            }
            else
            {
                move.setRightTrackPower(-1);
                move.setLeftTrackPower(-1);
            }
        }

    }

}
