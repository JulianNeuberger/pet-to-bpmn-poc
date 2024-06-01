package diagram.path;

import diagram.Vector2;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FourPointPath {
    public static Collection<Waypoint> getPath(ModelInstance modelInstance, Vector2 start, Vector2 end) {
        List<Waypoint> path = new ArrayList<>();
        // exit
        path.add(modelInstance.newInstance(Waypoint.class));
        // intermediate 1
        path.add(modelInstance.newInstance(Waypoint.class));
        // intermediate 2
        path.add(modelInstance.newInstance(Waypoint.class));
        // entry
        path.add(modelInstance.newInstance(Waypoint.class));

        Vector2 diff = start.sub(end);

        path.get(0).setX(start.getX());
        path.get(0).setY(start.getY());

        if (diff.getX() < diff.getY()) {
            path.get(1).setX(start.getX() + diff.getX() / 2d);
            path.get(1).setY(start.getY());

            path.get(2).setX(start.getX() + diff.getX() / 2d);
            path.get(2).setY(end.getY());
        } else {
            path.get(1).setX(start.getX());
            path.get(1).setY(start.getY() + diff.getY() / 2d);

            path.get(2).setX(end.getX());
            path.get(2).setY(start.getY() + diff.getY() / 2d);
        }

        path.get(3).setX(end.getX());
        path.get(3).setY(end.getY());

        return path;
    }
}
