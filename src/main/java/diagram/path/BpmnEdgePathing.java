package diagram.path;

import diagram.Vector2;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.Collection;

public class BpmnEdgePathing {
    public static Collection<Waypoint> getBestPath(ModelInstance modelInstance, BpmnShape source, BpmnShape target) {
        // TODO: maybe use center point of task?
        boolean targetAboveSource = source.getBounds().getY() + source.getBounds().getHeight() < target.getBounds().getY();
        boolean targetBelowSource = source.getBounds().getY() > target.getBounds().getY() + target.getBounds().getHeight();

        boolean targetLeftSource = source.getBounds().getX() > target.getBounds().getX() + target.getBounds().getWidth();
        boolean targetRightSource = source.getBounds().getX() + source.getBounds().getWidth() < target.getBounds().getX();

        /*
         1 | 2 | 3
        ---+---+---
         4 | s | 5
        ---+---+---
         6 | 7 | 8

         */


        // case 2:
        if (targetAboveSource && !targetLeftSource && !targetRightSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + source.getBounds().getWidth() / 2d,
                            source.getBounds().getY() + source.getBounds().getHeight()),
                    new Vector2(target.getBounds().getX() + target.getBounds().getWidth() / 2d,
                            target.getBounds().getY() + 0)
            );
        }

        // case 7:
        if (targetBelowSource && !targetLeftSource && !targetRightSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + source.getBounds().getWidth() / 2d,
                            source.getBounds().getY() + 0),
                    new Vector2(target.getBounds().getX() + target.getBounds().getWidth() / 2d,
                            target.getBounds().getY() + target.getBounds().getHeight())
            );
        }

        // case 4
        if (targetLeftSource && !targetAboveSource && !targetBelowSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + 0,
                            source.getBounds().getY() + source.getBounds().getHeight() / 2d),
                    new Vector2(target.getBounds().getX() + target.getBounds().getWidth(),
                            target.getBounds().getY() + target.getBounds().getHeight() / 2d)
            );
        }

        // case 5:
        if (targetRightSource && !targetAboveSource && !targetBelowSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + source.getBounds().getWidth(),
                            source.getBounds().getY() + source.getBounds().getHeight() / 2d),
                    new Vector2(target.getBounds().getX() + 0,
                            target.getBounds().getY() + target.getBounds().getHeight() / 2d)
            );
        }

        // case 1:
        if (targetAboveSource && targetLeftSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + source.getBounds().getWidth() / 2d,
                            source.getBounds().getY() + source.getBounds().getHeight()),
                    new Vector2(target.getBounds().getX() + target.getBounds().getWidth() / 2d,
                            target.getBounds().getY() + 0)
            );
        }

        // case 3:
        if (targetAboveSource && targetRightSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + source.getBounds().getWidth() / 2d,
                            source.getBounds().getY() + source.getBounds().getHeight()),
                    new Vector2(target.getBounds().getX() + target.getBounds().getWidth() / 2d,
                            target.getBounds().getY() + 0)
            );
        }

        // case 6:
        if (targetBelowSource && targetLeftSource) {
            return TwoPointPath.getPath(
                    modelInstance,
                    new Vector2(source.getBounds().getX() + source.getBounds().getWidth() / 2d,
                            source.getBounds().getY() + 0),
                    new Vector2(target.getBounds().getX() + target.getBounds().getWidth() / 2d,
                            target.getBounds().getY() + target.getBounds().getHeight())
            );
        }

        // case 8:
        return TwoPointPath.getPath(
                modelInstance,
                new Vector2(source.getBounds().getX() + source.getBounds().getWidth() / 2d,
                        source.getBounds().getY() + 0),
                new Vector2(target.getBounds().getX() + target.getBounds().getWidth() / 2d,
                        target.getBounds().getY() + target.getBounds().getHeight())
        );
    }
}
