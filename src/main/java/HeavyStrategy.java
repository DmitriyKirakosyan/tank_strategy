import model.*;

import java.awt.geom.Point2D;

/**
 * User: dmitriy
 */
public class HeavyStrategy implements IStrategyPerformer {

    private final int ABOUT_WALL_LENGTH = 100;

    private final int NUM_WAY_POINTS = 20;

    private final double MIN_ANGLE = Math.PI / 180;
    private final double MAX_TANK_DISTANCE_COEFFICIENT = 5;

    private final double TARGET_BONUS_RADIUS = 400;
    private final double BONUS_WALL_DISTANCE = 300;

    private final double TARGET_POINTS_RADIUS = 100;

    @Override
    public void perform(Tank self, World world, Move move)
    {
        //move tank
        this.moveTank(self, world, move);

        //attack
        this.shoot(self, world, move);

    }

    /* strategy functions */

    /**
     * Производит движение танка
     * @param self
     * @param world
     * @param move
     */
    private void moveTank(Tank self, World world, Move move)
    {
        Bonus targetBonus = this.findCloseBonus(self, world);
        if (targetBonus != null)
        {
            //System.out.println("move to bonus : " + targetBonus.getX() + ", " + targetBonus.getY());
            this.moveToPoint(self, move, targetBonus.getX(), targetBonus.getY());
        }
        else
        {
            Point2D point = this.chooseGoodPoint(this.getClosePoints(self), world, self);
            if (point != null)
            {
                //System.out.println("move to point : " + point.getX() + ", " + point.getY());
                //System.out.println("tank point : " + self.getX() + ", " + self.getY());
                this.moveToPoint(self, move, point.getX(), point.getY());
            }
            else
            {
                //System.out.println("move point is null");
            }
        }
    }

    private void shoot(Tank self, World world, Move move)
    {
        //turret controller
        Player targetPlayer = this.chooseTargetPlayer(world.getPlayers(), self.getPlayerName());

        Tank targetTank = this.findTankByPlayerName(targetPlayer.getName(), world.getTanks());

        if (targetTank.getCrewHealth() == 0 || targetTank.getHullDurability() == 0)
        {
            targetTank = this.getAliveTank(world.getTanks());
        }

        if (targetTank != null)
        {
            // System.out.println("actual target player : " + targetTank.getPlayerName() + ", try target player : " + targetPlayer.getName());

            move.setTurretTurn(self.getTurretAngleTo(targetTank) < 0 ? -1 : 1);
        }
        double tanksDistance = self.getDistanceTo(targetTank);
        if (tanksDistance > world.getWidth()) { tanksDistance = world.getWidth(); }
        double tanksDistanceCoef = world.getWidth() / tanksDistance;
        if (tanksDistanceCoef > MAX_TANK_DISTANCE_COEFFICIENT) { tanksDistanceCoef = MAX_TANK_DISTANCE_COEFFICIENT; }
        if (this.needShoot(tanksDistanceCoef, self.getTurretAngleTo(targetTank)) &&
            self.getRemainingReloadingTime() < 2)
        {
            move.setFireType(FireType.PREMIUM_PREFERRED);
        }

    }

    /**
     * Определяет ближайший бонус к танку, в радиусе TARGET_BONUS_RADIUS
     * @param self
     * @param world
     * @return
     */
    private Bonus findCloseBonus(Tank self, World world)
    {
        Bonus result = null;

        if (self.getCrewHealth() < self.getCrewMaxHealth() / 4)
        {
            //надо срочно искать больничку
            result = findCloseBonusByType(self, world.getBonuses(), BonusType.MEDIKIT);
            if (result != null) { return result; }
        }

        if (self.getHullDurability() < self.getHullMaxDurability() / 4)
        {
            //надо срочно искать больничку
            result = findCloseBonusByType(self, world.getBonuses(), BonusType.REPAIR_KIT);
            if (result != null) { return result; }
        }

        for (Bonus bonusItem : world.getBonuses())
        {
            if (self.getDistanceTo(bonusItem) < TARGET_BONUS_RADIUS &&
                    //возле стены ли бонус
                    this.isPointAboutWall(bonusItem.getX(), bonusItem.getY(), world.getWidth(), world.getHeight(), BONUS_WALL_DISTANCE))
            {
                if (result == null || self.getDistanceTo(result) > self.getDistanceTo(bonusItem))
                {
                    //проверка на нужность бонуса
                    if ((bonusItem.getType() != BonusType.MEDIKIT ||
                            self.getCrewHealth() < self.getCrewMaxHealth() - self.getCrewMaxHealth() / 4) &&
                            (bonusItem.getType() != BonusType.REPAIR_KIT ||
                                    self.getHullDurability() < self.getHullMaxDurability() - self.getHullMaxDurability() / 4))
                    {
                        result = bonusItem;
                    }
                }
            }
        }
        return result;
    }

    private Bonus findCloseBonusByType(Tank self, Bonus[] bonusList, BonusType bonusType)
    {
        Bonus result = null;
        for (Bonus bonusItem : bonusList)
        {
            if (bonusItem.getType().equals(bonusType) &&
                    result == null || self.getDistanceTo(result) > self.getDistanceTo(bonusItem))
            {
                result = bonusItem;
            }
        }
        return result;
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
        double angleToBonus = self.getAngleTo(x, y);


        if (Math.abs(angleToBonus) < MIN_ANGLE * 20)
        {
            //едем передом
            move.setLeftTrackPower(1.0);
            move.setRightTrackPower(1.0);
        }
        else if (Math.abs(angleToBonus) > Math.PI - MIN_ANGLE * 20)
        {
            //едем задом
            move.setLeftTrackPower(-1.0);
            move.setRightTrackPower(-1.0);
        }
        else
        {
            double angleLine = 2;

            double backTrackPowerCoef = Math.abs(angleToBonus) < Math.PI / angleLine ?
                    (Math.abs(angleToBonus) / Math.PI) :
                    ((Math.PI - Math.abs(angleToBonus)) / Math.PI);

            backTrackPowerCoef *= Math.pow(backTrackPowerCoef, 2);
            backTrackPowerCoef = 1 - backTrackPowerCoef;

            double backTrackPower = -1 + (2 - 2 * backTrackPowerCoef);

            //System.out.println("back track power coef : " + backTrackPowerCoef + ", angle : " + angleToBonus);
            //System.out.println("back track power : " + backTrackPower);


            //поворачиваем либо задом либо передом
            if (Math.abs(angleToBonus) < Math.PI / angleLine  && angleToBonus >= 0)
            {
                move.setLeftTrackPower(0.5);//(1);
                move.setRightTrackPower(backTrackPower);//(backTrackPower);
            }
            else if (Math.abs(angleToBonus) < Math.PI / angleLine  && angleToBonus < 0)
            {
                move.setLeftTrackPower(backTrackPower);//(backTrackPower);
                move.setRightTrackPower(0.5);//(1);
            }
            else if (Math.abs(angleToBonus) > Math.PI / angleLine  && angleToBonus >= 0)
            {
                move.setLeftTrackPower(-1);//(backTrackPower);
                move.setRightTrackPower(-backTrackPower/2);//(1);
            }
            else
            {
                move.setLeftTrackPower(-backTrackPower/2);//(1);
                move.setRightTrackPower(-1);//(backTrackPower);
            }
        }
    }

    /**
     * Возвращает true если точка расположена относительно стены на расстоянии,
     * меньшем ABOUT_WALL_LENGTH
     * @param x
     * @param y
     * @param wHeight
     * @param wWidth
     * @return
     */
    private boolean isPointAboutWall(double x, double y, double wWidth, double wHeight, double distance)
    {
        return x < distance || y < distance ||
                x > wWidth - distance || y > wHeight - distance;
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
    private Tank getAliveTank(Tank[] tanks)
    {
        for (Tank tank : tanks)
        {
            if (!tank.isTeammate() && tank.getCrewHealth() != 0 && tank.getHullDurability() != 0)
            {
                return tank;
            }
        }
        return null;
    }

    /**
     * Определяет надо ли стрелять
     * @param tanksDistanceCoefficient
     * @param angleToEnemy
     * @return
     */
    private boolean needShoot(double tanksDistanceCoefficient, double angleToEnemy)
    {
        return Math.abs(angleToEnemy) < MIN_ANGLE * tanksDistanceCoefficient;
    }

    /**
     * Возвращает ближние точки танка
     * @param self
     * @return
     */
    private Point2D[] getClosePoints(Tank self)
    {
        Point2D[] result = new Point2D[NUM_WAY_POINTS];
        double x;
        double y;
        double angle;
        for (int i = 0; i < NUM_WAY_POINTS; ++i)
        {
            angle = (Math.PI * 2) / NUM_WAY_POINTS * i;
            x = self.getX() + 100 * Math.cos(angle);
            y = self.getY() + 100 * Math.sin(angle);
            result[i] = new Point2D.Double(x, y);
        }
        return result;
    }

    /**
     * Найти самую далекую точку из списка относительно всех танков,
     * и не достаточно близкую к стене
     * @param points
     * @param world
     * @return
     */
    private Point2D chooseGoodPoint(Point2D[] points, World world, Tank self)
    {
        Tank[] tanks = world.getTanks();
        int i = 0;

        //находим суммарную дистрацию для каждой точки
        double[] distances = new double[points.length];
        for (i = 0; i < points.length; ++i)
        {
            distances[i] = 0d;
            for (Tank tank : tanks)
            {
                if (!tank.isTeammate())
                {
                    distances[i] += tank.getDistanceTo(points[i].getX(), points[i].getY());
                }
            }
        }

        //сортируем расстояния и соответсвующие точки по убыванию
        boolean sorted = false;
        double swapDistance;
        Point2D swapPoint;
        while (!sorted)
        {
            sorted = true;
            for (i = 0; i < distances.length - 2; ++i)
            {
                if (this.compareForGoodPoint(points[i], distances[i], distances[i + 1], self))
                {
                    sorted = false;
                    swapDistance = distances[i];
                    distances[i] = distances[i + 1];
                    distances[i + 1] = swapDistance;

                    swapPoint = points[i];
                    points[i] = points[i + 1];
                    points[i + 1] = swapPoint;
                }
            }
        }

        //находим точку, которая не у стены
        Point2D resultPoint = null;
        for (i = 0; i < distances.length; ++i)
        {
            if (!this.isPointAboutWall(points[i].getX(), points[i].getY(), world.getWidth(), world.getHeight(), ABOUT_WALL_LENGTH))
            {
                resultPoint = points[i];
                break;
            }
            else
            {
                //System.out.println("point is about wall : " + points[i].getX() + ", " + points[i].getY());
            }
        }

        return resultPoint;
    }

    /**
     * Определяет приоритет одной точки над другой
     * @param point1 первая точка
     * @param pointDistance1 суммарное расстояние всех танков до первой точки
     * @param pointDistance2 суммарное расстояние всех танков до второй точки
     * @param self собственный танк
     * @return true если нужно вторую точку поставить перед первой по приоритету
     */
    private boolean compareForGoodPoint(Point2D point1, double pointDistance1,
                                        double pointDistance2, Tank self)
    {

        return pointDistance1 < pointDistance2;
    }

}
