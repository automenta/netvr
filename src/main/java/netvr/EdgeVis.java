package netvr;

import com.bulletphysics.collision.broadphase.DbvtAabbMm;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.jogamp.opengl.GL2;

import javax.vecmath.Vector3f;

public class EdgeVis extends Vis {
    final int id;
    final float lat, lon, lat2, lon2;
    final float[] color = new float[3];
    float thick = 1;


    public EdgeVis(AllEdgesIterator edge, double lat, double lon, double lat2, double lon2) {
        this.id = edge.getEdge();
        this.lat = (float) lat;
        this.lon = (float) lon;
        this.lat2 = (float) lat2;
        this.lon2 = (float) lon2;


//            float cx = (float) ((lon + lon2) / 2);
//            float cy = (float) ((lat + lat2) / 2);
//
//            final Transform t = new Transform();
//
//            final Quat4f rot = new Quat4f();
//            rot.set(new AxisAngle4f(0, 0, 1,
//                    (float) Math.atan2(lat2 - lat, lon2 - lon)));
//            t.setRotation(rot);
//
//            t.origin.set(cx - 20, cy - 33, 0);
//
//            double dx = abs(lat - lat2);
//            double dy = abs(lon - lon2);
//            float thick = 0.05f; //length / 3; //HACK
//            float density =
//                    0;
//            //10;
//            float margin = thick/2;
//            float length = Math.max(0, (float) (Math.sqrt(dx * dx + dy * dy) - margin));
//            w.localCreateRigidBody((float) (density * length * thick * thick * Math.PI), t, new CylinderShapeX(
//                    new Vector3f(length / 2, thick / 2, thick / 2)));

    }

    @Override
    public DbvtAabbMm box() {
        return new DbvtAabbMm().set(new Vector3f(lon, lat, 0), new Vector3f(lon2, lat2, 0));
    }

    @Override
    public void draw(GL2 gl) {
        gl.glBegin(GL2.GL_LINES);
        gl.glColor3fv(color, 0);
        gl.glVertex3f(lon, lat, 0);
        gl.glVertex3f(lon2, lat2, 0);
        gl.glEnd();
    }
}
