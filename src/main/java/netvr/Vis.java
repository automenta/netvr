package netvr;

import com.bulletphysics.collision.broadphase.DbvtAabbMm;
import com.jogamp.opengl.GL2;

public abstract class Vis {
    public abstract DbvtAabbMm box();

    public abstract void draw(GL2 gl);
}
