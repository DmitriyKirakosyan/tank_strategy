import model.*;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.awt.geom.Point2D;

/**
 * User: dmitriy
 */
public class AttackPriorityStrategy implements IStrategyPerformer {

    private final double BONUS_ANGLE = Math.PI / 8;

    private final double ENEMY_CRITICAL_HEALTH = 40;

    private final double MIN_ANGLE = Math.PI / 180;
    private final double MIN_SHELL_DODGE_ANGLE = Math.PI / 20;
    private final double MAX_SHELL_DODGE_ANGLE = Math.PI / 2;

    private final double MAX_TANKS_DISTANCE = 1500;

    private final double IN_CENTER_DISTANCE = 200;
    private final double IN_ANGLE_DISTANCE = 140;

    private final double WALL_DISTANCE_FOR_MOVE_TO_ANGLE = 100;

    /**
     * Угол, в рамках которого цель считается под прицелом
     */
    private final double CATCH_ANGLE = Math.PI / 50;

    /**
     * На сколько уменьшаем расстояние до цели, если она под прицелом
     */
    private final double TARGET_ENEMY_PIXEL_PENALTY = 200;

    private final double MIN_ANGLE_FOR_MOVE = Math.PI / 10;

    private final int MAX_TANKS_FOR_NEEDED_WALK = 2;

    private final int MAX_TANKS_FOR_SAFE_WALK = 3;

    /**
     * Сколько максимум должно соять танков врагов на поле, чтобы танк мог менять цель для выстрела,
     * например при желании добить того, у кого очень мало жизней
     */
    private final int MAX_TANKS_FOR_CHANGE_TARGET = 3;

    private final TankMoveUtil _moveUtil = new TankMoveUtil();

    @Override
    public void perform(Tank self, World world, Move move)
    {

        int numAliveEnemies = this.getNumAliveEnemyTanks(world.getTanks());
        boolean allTanksAlive = this.getNumAliveEnemyTanks(world.getTanks()) == world.getTanks().length - 1;
        boolean canSafeWalk = numAliveEnemies <= MAX_TANKS_FOR_SAFE_WALK;
        boolean moved = false;

        //Если мы не в центре или убит хотябы один танк, то уворачиваемся от пуль
        //самая приоритетная задача
        if (!this.tankInCenter(self, world, IN_CENTER_DISTANCE) || !allTanksAlive)
        {
            //dodge
            moved = this.dodge(self, world, move);
        }

        //move for bonus
        //если игроков достаточно мало, можно ездить за бонусом
        if (!moved && canSafeWalk)
        {
            moved = this.tryMoveForBonus(self, world, move);
        }

        //attack
        double shootAngle = this.shoot(self, world, move);

        //если никуда не едем, значит крутимся в сторону цели для выстрела
        if (!moved && Math.abs(shootAngle) > CATCH_ANGLE && !allTanksAlive)
        {
            this.rotateTankToAngle(shootAngle, move);
            moved = true;
        }

        //move to angle
        if (!moved &&
                !this.inAngle(self, world) &&
                //!allTanksAlive &&//!this.tankInCenter(self, world, WALL_DISTANCE_FOR_MOVE_TO_ANGLE) &&
                !canSafeWalk)
        {
            Point2D anglePoint = this.getPointInClosestAngle(self, world);
            //System.out.println("move to angle, point : " + anglePoint.getX() + ", " + anglePoint.getY());
            _moveUtil.moveToPoint(self, move, anglePoint.getX(), anglePoint.getY(), MIN_ANGLE_FOR_MOVE);
            //this.moveToPoint(self, move, anglePoint.getX(), anglePoint.getY());
            moved = true;
        }

        //повернем танк перпендикулярно к цели, чтоб проще было увернуться
        if (!moved && (!this.tankInCenter(self, world, IN_CENTER_DISTANCE) || !allTanksAlive))
        {
            Tank tank = this.getClosestAggressiveTank(self, world.getTanks(), true);
            if (tank != null)
            {
                moved = this.rotateTankRelativelyEnemy(self, tank, move, Math.PI / 2);
            }
        }

        //если угол пушки близок к цели и никуда не едем, то едем назад
        if (!moved)
        {
            Point2D[] points = _moveUtil.getClosePoints(self, 30);
            Point2D goodPoint = _moveUtil.chooseGoodPoint(points, world, self);
            _moveUtil.moveToPoint(self, move, goodPoint.getX(), goodPoint.getY(), MIN_ANGLE_FOR_MOVE);
            //System.out.println("move to good point");
            //this.moveToPoint(self, move, goodPoint.getX(), goodPoint.getY());
            //this.moveTankBackward(move);
        }
    }

    /* strategy functions */

    /**
     * Уклоняется от пули, если есть необходимость
     * @param self
     * @param world
     * @param move
     * @return  возвращает true если уклонение состоялось
     */
    private boolean dodge(Tank self, World world, Move move)
    {
        boolean result = false;
        Shell shellForDodge = null;
        Shell[] shells = world.getShells();
        for (Shell shell : shells)
        {
            double distanceToSHell = self.getDistanceTo(shell);
            double distanceCoef = distanceToSHell / MAX_TANKS_DISTANCE;
            if (Math.abs(shell.getAngleTo(self)) < MIN_SHELL_DODGE_ANGLE +
                    ((MAX_SHELL_DODGE_ANGLE - MIN_SHELL_DODGE_ANGLE) * distanceCoef))
            {
                shellForDodge = shell;
                break;
            }
        }

        if (shellForDodge != null)
        {
            this.dodgeShell(self, world, move, shellForDodge);
            result = true;
        }
        else
        {
            //если нет летящей пули, пытаемся увернутся от потенциальной путем поворота
            Tank closestAggressiveTank = this.getClosestAggressiveTank(self, world.getTanks(), false);
            if (closestAggressiveTank != null)
            {
                this.dodgePotentialShootWithTern(self, closestAggressiveTank, move);
                result = true;
            }
        }
        return result;
    }

    private void dodgeShell(Tank self, World world, Move move, Shell shellForDodge)
    {
        boolean moveForward;
        if (this.isWallRearTank(self, world))
        {
            moveForward = true;
        }
        else
        {
            if (shellForDodge.getAngleTo(self) > 0)
            {
                moveForward = self.getAngleTo(shellForDodge) > 0;
            }
            else
            {
                moveForward = ! (self.getAngleTo(shellForDodge) > 0);
            }
        }
        if (moveForward)
        {
            this.moveTankForward(move);
        }
        else
        {
            this.moveTankBackward(move);
        }
    }

    private void dodgePotentialShootWithTern(Tank self, Tank enemy, Move move)
    {
        double enemyGunAngle = enemy.getTurretAngleTo(self);
        double angleToEnemy = self.getAngleTo(enemy);
        if ( enemyGunAngle > 0)
        {
            if ((angleToEnemy > 0 && angleToEnemy < Math.PI / 2) ||
                    (angleToEnemy < -Math.PI / 2))
            {
                this.rotateTankRight(move);
            }
            else
            {
                if (angleToEnemy > Math.PI / 2) {
                    this.moveTankForward(move);
                } else {
                    this.moveTankBackward(move);
                }
            }
        }
        else
        {
            if (angleToEnemy > Math.PI / 2 ||
                    (angleToEnemy < 0 && angleToEnemy > -Math.PI / 2))
            {
                this.rotateTankLeft(move);
            }
            else
            {
                if (angleToEnemy < Math.PI / 2) {
                    this.moveTankBackward(move);
                } else {
                    this.moveTankForward(move);
                }
            }
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
        int numAliveEnemies = this.getNumAliveEnemyTanks(world.getTanks());
        Bonus resultBonus = null;
        for (Bonus bonus : world.getBonuses())
        {
            //если игроков еще много, лучше не высовываться по пустякам :)
            if (numAliveEnemies <= MAX_TANKS_FOR_NEEDED_WALK || this.isUsefulBonus(self, bonus))
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

        }

        if (resultBonus != null)
        {
            //System.out.println("angle to bonus " + self.getAngleTo(resultBonus));
            this.moveToPoint(self, move, resultBonus.getX(), resultBonus.getY());
            return true;
        }
        return false;
    }

    /**
     * Управляет пушкой
     * @param self
     * @param world
     * @param move
     * @return возвращает угол, на который поворачивается пушка в данный момент
     */
    private double shoot(Tank self, World world, Move move)
    {
        Tank tank = null;
        if (this.getNumAliveEnemyTanks(world.getTanks()) <= MAX_TANKS_FOR_CHANGE_TARGET)
        {
            tank = this.getMinHealthTank(world.getTanks());
        }
        //if (tank != null) { System.out.println("min health tank founded"); }
        if (tank == null)
        {
            tank = this.getClosestTankWithAngle(self, world.getTanks());
        }

        if (tank != null)
        {
            double turretAngleToTank = self.getTurretAngleTo(tank);
            move.setTurretTurn(turretAngleToTank);

            if (Math.abs(turretAngleToTank) < MIN_ANGLE &&
                !this.hasDeadTankBetweenSelfAndEnemy(self, tank, world.getTanks()))
            {
                //System.out.println("shoooot");
                move.setFireType(FireType.PREMIUM_PREFERRED);
            }

            return turretAngleToTank;
        }
        return 0;
    }

    /**
     * Поворачивает танк к нужному углу
     * @param angle
     * @param move
     */
    private void rotateTankToAngle(double angle, Move move)
    {
        if (angle > 0)
        {
            this.rotateTankRight(move);
        }
        else if (angle < 0)
        {
            this.rotateTankLeft(move);
        }
    }

    private void rotateTankRight(Move move)
    {
        move.setLeftTrackPower(0.75);
        move.setRightTrackPower(-1);
    }

    private void rotateTankLeft(Move move)
    {
        move.setLeftTrackPower(-1);
        move.setRightTrackPower(0.75);
    }

    private void moveTankForward(Move move)
    {
        move.setRightTrackPower(1);
        move.setLeftTrackPower(1);
    }

    private void moveTankBackward(Move move)
    {
        move.setLeftTrackPower(-1);
        move.setRightTrackPower(-1);
    }

    /**
     * Поворачивает танк относительно другого танка под углом angle
     * @param self
     * @param enemy
     * @param move
     * @param relativelyAngle
     * @return true, если поворот осуществляется
     */
    private boolean rotateTankRelativelyEnemy(Tank self, Tank enemy, Move move, double relativelyAngle)
    {
        boolean result = false;
        double currentAngle = self.getAngleTo(enemy);
        double angle = MIN_ANGLE_FOR_MOVE / 2;

        if ((currentAngle < 0 && currentAngle > -relativelyAngle + angle) ||
             (currentAngle > relativelyAngle + angle))
        {
            this.rotateTankRight(move);
            result = true;
        }
        else if ((currentAngle > 0 && currentAngle < relativelyAngle - angle) ||
                 (currentAngle < -relativelyAngle - angle))
        {
            this.rotateTankLeft(move);
            result = true;
        }
        return result;
    }

    /**
     * Возвращает true, если бонус необходим
     * @param self
     * @param bonus
     * @return
     */
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

    /**
     * Возвращает количество живых танков, не считая свои
     * @param tanks
     * @return
     */
    private int getNumAliveEnemyTanks(Tank[] tanks)
    {
        int result = 0;
        for (Tank tank : tanks)
        {
            if (!tank.isTeammate() && tank.getCrewHealth() > 0 && tank.getHullDurability() > 0)
            {
                ++result;
            }
        }
        return result;
    }

    /**
     * Возвращает танк, у которого минимальное количество жизней
     * @param tanks
     * @return
     */
    private Tank getMinHealthTank(Tank[] tanks)
    {
        Tank result = null;
        for (Tank tank : tanks)
        {
            if (this.isActualEnemyTank(tank))
            {
                if (tank.getHullDurability() <= ENEMY_CRITICAL_HEALTH / 2 ||
                        tank.getCrewHealth() <= ENEMY_CRITICAL_HEALTH / 2)
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

    /**
     * Возвращает ближайший танк для выстрела, учитывая поворот собственной пушки
     * @param self
     * @param tanks
     * @return
     */
    private Tank getClosestTankWithAngle(Tank self, Tank[] tanks)
    {
        Tank result = null;
        double previousDistance = 10e10;
        double currentDistance;
        for (Tank tank : tanks)
        {
            if (this.isActualEnemyTank(tank))
            {
                currentDistance = self.getDistanceTo(tank);
                //если цель под прицелом, уменьшаем ее расстояние
                if (Math.abs(self.getTurretAngleTo(tank.getX(), tank.getY())) < CATCH_ANGLE)
                {
                    //System.out.println("pixel penalty");
                    currentDistance -= TARGET_ENEMY_PIXEL_PENALTY;
                }


                if (result == null || currentDistance < previousDistance)
                {
                    result = tank;
                    previousDistance = currentDistance;
                }
            }
        }
        return result;
    }

    /**
     * Возвращает ближайший опасный для игрока танк
     * @param self
     * @param tanks
     * @return
     */
    private Tank getClosestAggressiveTank(Tank self, Tank[] tanks, boolean notShootingYet)
    {
        Tank result = null;
        double prevDistance = 0;
        for (Tank tank : tanks)
        {
            boolean ignoreThisTank = !notShootingYet || tank.getRemainingReloadingTime() > tank.getReloadingTime() / 1.5;
            if (!ignoreThisTank && !tank.isTeammate() &&
                    Math.abs(tank.getTurretAngleTo(self)) < CATCH_ANGLE)
            {
                if (result == null || (prevDistance > self.getDistanceTo(tank)))
                {
                    result = tank;
                    prevDistance = self.getDistanceTo(tank);
                }
            }
        }
        return result;
    }

    /**
     * Возвращает точку ближайшего угла
     * Точка в углу определяется на основе IN_ANGLE_DISTANCE
     * @param tank
     * @param world
     * @return
     */
    private Point2D getPointInClosestAngle(Tank tank, World world)
    {
        Point2D result = null;
        double prevDistance = 0;
        double currentDistance;
        Point2D[] points = new Point2D[4];
        points[0] = new Point2D.Double(world.getWidth() - IN_ANGLE_DISTANCE, world.getHeight() - IN_ANGLE_DISTANCE);
        points[1] = new Point2D.Double(IN_ANGLE_DISTANCE, world.getHeight() - IN_ANGLE_DISTANCE);
        points[2] = new Point2D.Double(IN_ANGLE_DISTANCE, IN_ANGLE_DISTANCE);
        points[3] = new Point2D.Double(world.getWidth() - IN_ANGLE_DISTANCE, IN_ANGLE_DISTANCE);

        for (Point2D point : points)
        {
            currentDistance = tank.getDistanceTo(point.getX(), point.getY());
            if (result == null || currentDistance < prevDistance)
            {
                result = point;
                prevDistance = currentDistance;
            }
        }

        //System.out.println("angle point for move : " + result.getX() + ", " + result.getY());

        return result;
    }

    /**
     * Если это враг и он жив, возвращает true
     * @param tank
     * @return
     */
    private boolean isActualEnemyTank(Tank tank)
    {
        return !tank.isTeammate() && this.isAliveTank(tank);
    }

    /**
     * Если жив, возвращает true
     * @param tank
     * @return
     */
    private boolean isAliveTank(Tank tank)
    {
        return tank.getCrewHealth() > 0 && tank.getHullDurability() > 0;
    }

    /**
     * Возвращает true, если между целью и стреляющим стоит дохлый танк
     * @param self
     * @param enemy
     * @param tanks
     * @return
     */
    private boolean hasDeadTankBetweenSelfAndEnemy(Tank self, Tank enemy, Tank[] tanks)
    {
        double enemyDistance = self.getDistanceTo(enemy);
        for (Tank tank : tanks)
        {
            if (tank != enemy && !this.isAliveTank(tank))
            {
                if (self.getDistanceTo(tank) < enemyDistance && Math.abs(self.getTurretAngleTo(tank)) < CATCH_ANGLE)
                {
                    //System.out.println("has dead tank with angle : " + self.getTurretAngleTo(tank));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Возвращает true, если такт стоит спиной к стене
     * @param tank
     * @param world
     * @return
     */
    private boolean isWallRearTank(Tank tank, World world)
    {
        double closeNum = 20;

        double tankBackX = tank.getX() + (tank.getWidth()/2) * Math.cos(Math.PI - tank.getAngle());
        double tankBackY = tank.getY() - (tank.getWidth()/2) * Math.sin(Math.PI - tank.getAngle());

        return tankBackX < closeNum || tankBackX > world.getWidth() - closeNum ||
               tankBackY < closeNum || tankBackY > world.getHeight() - closeNum;

    }

    /**
     * Возвращает true, если танк на данный момнет находится в углу
     * @param tank
     * @param world
     * @return
     */
    private boolean inAngle(Tank tank, World world)
    {
        return ((tank.getX() < IN_ANGLE_DISTANCE && tank.getY() < IN_ANGLE_DISTANCE) ||
                (tank.getX() < IN_ANGLE_DISTANCE && tank.getY() > world.getHeight() - IN_ANGLE_DISTANCE) ||
                (tank.getX() > world.getWidth() - IN_ANGLE_DISTANCE && tank.getY() < IN_ANGLE_DISTANCE) ||
                (tank.getX() > world.getWidth() - IN_ANGLE_DISTANCE && tank.getY() > world.getHeight() - IN_ANGLE_DISTANCE));
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
                this.rotateTankRight(move);
            }
            else if (angleToPoint < -MIN_ANGLE_FOR_MOVE)
            {
                this.rotateTankLeft(move);
            }
            else
            {
                this.moveTankForward(move);
            }
        }
        else
        {
            if (angleToPoint > 0 && angleToPoint < Math.PI - MIN_ANGLE_FOR_MOVE)
            {
                this.rotateTankLeft(move);
            }
            else if (angleToPoint < 0 && angleToPoint > -Math.PI - MIN_ANGLE_FOR_MOVE)
            {
                this.rotateTankRight(move);
            }
            else
            {
                this.moveTankBackward(move);
            }
        }
    }

    /**
     * Возвращает true, если танк находится в центре
     * @param tank
     * @param world
     * @return
     */
    private boolean tankInCenter(Tank tank, World world, double distanceToWall)
    {
        return tank.getX() > distanceToWall && tank.getX() < world.getWidth() - distanceToWall &&
               tank.getY() > distanceToWall && tank.getY() < world.getHeight() - distanceToWall;
    }

}
