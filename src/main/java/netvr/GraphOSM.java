package netvr;

import com.bulletphysics.collision.broadphase.Dbvt;
import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;

import java.io.File;

public class GraphOSM extends GraphHopperOSM {
    public final Dbvt<Vis> ways = new Dbvt<>();

    GraphHopperStorage ramGraph(String directory, EncodingManager encodingManager, boolean is3D, boolean turnRestrictionsImport) {
        return new GraphHopperStorage(new RAMDirectory(directory, false),
                encodingManager, is3D, turnRestrictionsImport);
    }
    @Override public boolean load(String graphHopperFolder) {
        boolean l = super.load(graphHopperFolder); //HACK

        FullWayReader reader = new FullWayReader(
                ramGraph("x", getEncodingManager(), true, false));


        reader.setFile(new File(getOSMFile()));
        //reader.setWorkerThreads(Runtime.getRuntime().availableProcessors());
        try {
            reader.readGraph();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return l; //disable caching
    }


    private class FullWayReader extends OSMReader {
        final NodeAccess nodeAccess = getGraphHopperStorage().getNodeAccess();

        public FullWayReader(GraphHopperStorage ghStorage) {
            super(ghStorage);
        }

        @Override
        protected void processNode(ReaderNode node) {
            super.processNode(node);
        }

        @Override
        protected void processRelation(ReaderRelation relation) {

        }

        @Override
        protected void processWay(ReaderWay way) {
            //super.processWay(way);
            final LongArrayList wn = way.getNodes();
            if (wn.size() > 2
                    //way.getTag("building", null)!=null
            ) {
                final WayVis v = new WayVis(wn, getNodeMap(), nodeAccess);
                if (v.poly.size()>2)
                    add(v);
            }
        }
    }

    public void add(Vis v) {
        ways.put(v, v.box());
    }
}
