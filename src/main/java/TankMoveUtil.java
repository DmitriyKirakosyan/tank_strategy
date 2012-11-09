import model.Move;
import model.Tank;
import model.World;

import java.awt.geom.Point2D;

/**
 * User: dmitriy
 * Date: 11/9/12
 * Time: 12:17 AM
 */
public class TankMoveUtil {
    private final int ABOUT_WALL_LENGTH = 100;

    /**
     * Возвращает ближние точки танка
     * @param self
     * @return
     */
    public Point2D[] getClosePoints(Tank self, int numPoints)
    {
        Point2D[] result = new Point2D[numPoints];
        double x;
        double y;
        double angle;
        for (int i = 0; i < numPoints; ++i)
        {
            angle = (Math.PI * 2) / numPoints * i;
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
    public Point2D chooseGoodPoint(Point2D[] points, World world, Tank self)
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
     * Двигает танк в сторону точки
     * @param self
     * @param move
     * @param x
     * @param y
     */
    public void moveToPoint(Tank self, Move move, double x, double y, double minAngle)
    {
        double angleToBonus = self.getAngleTo(x, y);


        if (Math.abs(angleToBonus) < minAngle)
        {
            //едем передом
            move.setLeftTrackPower(1.0);
            move.setRightTrackPower(1.0);
        }
        else if (Math.abs(angleToBonus) > Math.PI - minAngle)
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


}
