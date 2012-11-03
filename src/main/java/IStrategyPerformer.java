import model.Move;
import model.Tank;
import model.World;

/**
 * User: dmitriy
 */
public interface IStrategyPerformer {
    void perform(Tank self, World world, Move move);
}
