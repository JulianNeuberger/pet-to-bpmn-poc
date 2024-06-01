package diagram.path;

import diagram.Vector2;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TwoPointPath {
    public static Collection<Waypoint> getPath(ModelInstance modelInstance, Vector2 start, Vector2 end) {
        List<Waypoint> path = new ArrayList<>();
        // exit
        path.add(modelInstance.newInstance(Waypoint.class));
        // entry
        path.add(modelInstance.newInstance(Waypoint.class));


        path.get(0).setX(start.getX());
        path.get(0).setY(start.getY());

        path.get(1).setX(end.getX());
        path.get(1).setY(end.getY());

        return path;
    }
}
