/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package netvr;


import com.bulletphysics.collision.broadphase.AxisSweep3;
import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.shapes.simple.BoxShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.DynamicsWorld;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.ui.JOGL;
import com.bulletphysics.ui.SpaceGraph3D;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.vecmath.Vector3f;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * A rough graphical user interface for visualizing the OSM graph. Mainly for debugging algorithms
 * and spatial data structures. See e.g. this blog post:
 * https://graphhopper.com/blog/2016/01/19/alternative-roads-to-rome/
 * <p>
 * Use the web module for a better/faster/userfriendly/... alternative!
 * <p>
 *
 * @author Peter Karich
 */
class GraphHopperGL {


    private static GraphHopperConfig config(String[] args) {
        PMap a = PMap.read(args);
        a.putObject("datareader.file", a.getString("datareader.file", "/home/me/graphhopper/core/files/monaco.osm.gz"));
        a.putObject("graph.location", a.getString("graph.location", "/home/me/graphhopper/tools/target/mini-graph-ui-gh"));
        a.putObject("graph.flag_encoders", a.getString("graph.flag_encoders", "car"));

        return config(a);
    }

    private static final Logger logger = LoggerFactory.getLogger(GraphHopperGL.class);

    private GraphicsWrapper mg;

    private final GraphHopper hopper;
    private final Graph graph;
    private final LocationIndexTree index;
    private final NodeAccess na;
    private final DecimalEncodedValue avSpeedEnc;
    private final BooleanEncodedValue accessEnc;


    private Path path;
    private final boolean showTiles = false;
    private static final boolean plotNodes = true;
    private static final boolean useCH = false;

    private static final Color[] speedColors = generateColors(15);

    private volatile int currentPosX;
    private volatile int currentPosY;
    private volatile Snap fromRes, toRes;


    private GraphHopperGL(GraphHopper hopper) {
        this.hopper = hopper;
        this.graph = hopper.getGraphHopperStorage();
        this.na = graph.getNodeAccess();

        FlagEncoder encoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);
        avSpeedEnc = encoder.getAverageSpeedEnc();
        accessEnc = encoder.getAccessEnc();


        mg = new GraphicsWrapper(graph);

        // prepare node quadtree to 'enter' the graph. create a 313*313 grid => <3km
//         this.index = new DebugLocation2IDQuadtree(roadGraph, mg);
        this.index = (LocationIndexTree) hopper.getLocationIndex();
    }


    private GraphHopperGL(GraphHopperConfig c) {
        this(new GraphHopperOSM().init(c).importOrLoad());
    }

    public GraphHopperGL(String... args) {
        this(config(args));
    }

    MouseWheelListener mouseWheelScale = e -> {
        mg.scale(e.getX(), e.getY(), e.getWheelRotation() < 0);
        repaint();
    };
    MouseAdapter mouseGestures = new MouseAdapter() {
        // for routing:
        double fromLat, fromLon;
        boolean fromDone = false;
        boolean dragging = false;

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!fromDone) {
                fromLat = mg.getLat(e.getY());
                fromLon = mg.getLon(e.getX());
            } else {
                SwingUtilities.invokeLater(() -> {
                    double toLat = mg.getLat(e.getY());
                    double toLon = mg.getLon(e.getX());
                    StopWatch sw = new StopWatch().start();
                    logger.info("start searching from {},{} to {},{}", fromLat, fromLon, toLat, toLon);
                    // get from and to node id
                    fromRes = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
                    toRes = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
                    logger.info("found ids {} -> {} in {}s", fromRes, toRes, sw.stop().getSeconds());
                    repaint();
                });

            }

            fromDone = !fromDone;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            dragging = true;
            update(e);
            position(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging) {
                // update only if mouse release comes from dragging! (at the moment equal to fastPaint)
                dragging = false;
                update(e);
            }
        }

        void update(MouseEvent e) {
            mg.setNewOffset(e.getX() - currentPosX, e.getY() - currentPosY);
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            position(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            position(e);
        }
    };

    /**
     * a default config
     */
    private static GraphHopperConfig config(PMap a) {
        GraphHopperConfig c = new GraphHopperConfig(a);
        c.setProfiles(List.of(
                new Profile("profile")
                        .setVehicle("car")
                        .setWeighting("fastest")
        ));
        c.setCHProfiles(List.of(new CHProfile("profile")));
        c.setLMProfiles(List.of(new LMProfile("profile")));
        return c;
    }

    private static Color[] generateColors(int n) {
        Color[] cols = new Color[n];
        for (int i = 0; i < n; i++) {
            cols[i] = Color.getHSBColor(i / ((float) n), 0.85f, 1.0f);
        }
        return cols;
    }

    public void paintQuery(final Graphics2D g2) {
        if (fromRes == null || toRes == null)
            return;


        RoutingAlgorithm algo = router(hopper);

        QueryGraph qGraph = QueryGraph.create(graph, fromRes, toRes);

        StopWatch sw = new StopWatch().start();
        logger.info("start searching with {} from:{} to:{}", algo, fromRes, toRes);


        Color red = Color.red.brighter();
        g2.setColor(red);
        mg.plotNode(g2, qGraph.getNodeAccess(), fromRes.getClosestNode(), red, 10, "");
        mg.plotNode(g2, qGraph.getNodeAccess(), toRes.getClosestNode(), red, 10, "");

        g2.setColor(Color.blue.brighter().brighter());
        path = algo.calcPath(fromRes.getClosestNode(), toRes.getClosestNode());
        sw.stop();

        // if directed edges
        if (!path.isFound()) {
            logger.warn("path not found! direction not valid?");
            return;
        }

        logger.info("found path in {}s with nodes:{}, millis: {}, visited nodes:{}", sw.getSeconds(), path.calcNodes().size(), path.getTime(), algo.getVisitedNodes());
        g2.setColor(red);
        mg.plotPath(path, g2, 4);
    }

    public void paintRoads(final Graphics2D g2) {

        Rectangle d = null; //getBounds();
        BBox b = mg.setBounds(0, d.width, 0, d.height);

        g2.setColor(Color.black);


        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {

            int i = edge.getBaseNode();
            double lat = na.getLatitude(i);
            double lon = na.getLongitude(i);

            int j = edge.getAdjNode();
            double lat2 = na.getLatitude(j);
            double lon2 = na.getLongitude(j);

            if (b.contains(lat, lon) || b.contains(lat2, lon2))
                plotEdge(g2, edge);
        }

        if (showTiles) {
            index.query(graph.getBounds(), new LocationIndexTree.Visitor() {
                @Override
                public boolean isTileInfo() {
                    return true;
                }

                @Override
                public void onTile(BBox bbox, int depth) {
                    int width = Math.max(1, Math.min(4, 4 - depth));
                    g2.setColor(Color.GRAY);
                    mg.plotEdge(g2, bbox.minLat, bbox.minLon, bbox.minLat, bbox.maxLon, width);
                    mg.plotEdge(g2, bbox.minLat, bbox.maxLon, bbox.maxLat, bbox.maxLon, width);
                    mg.plotEdge(g2, bbox.maxLat, bbox.maxLon, bbox.maxLat, bbox.minLon, width);
                    mg.plotEdge(g2, bbox.maxLat, bbox.minLon, bbox.minLat, bbox.minLon, width);
                }

                @Override
                public void onNode(int node) {
                    //mg.plotNode(g2, node, Color.BLUE);
                }
            });

        }

        g2.setColor(Color.BLACK);
    }

    private void plotEdge(Graphics2D g2, AllEdgesIterator edge) {
        // mg.plotText(g2, lat * 0.9 + lat2 * 0.1, lon * 0.9 + lon2 * 0.1, iter.getName());
        //mg.plotText(g2, lat * 0.9 + lat2 * 0.1, lon * 0.9 + lon2 * 0.1, "s:" + (int) encoder.getSpeed(iter.getFlags()));
        double speed = edge.get(avSpeedEnc);
        Color color;
        if (speed >= 120) {
            color = speedColors[12]; // red
        } else if (speed >= 100) {
            color = speedColors[10];
        } else if (speed >= 80) {
            color = speedColors[8];
        } else if (speed >= 60) {
            color = speedColors[6];
        } else if (speed >= 50) {
            color = speedColors[5];
        } else if (speed >= 40) {
            color = speedColors[4];
        } else if (speed >= 30) {
            color = Color.GRAY;
        } else {
            color = Color.LIGHT_GRAY;
        }

        g2.setColor(color);
        float width = speed > 90 ? 2 : 1;

        mg.plotWayGeometry(g2,
                edge.get(accessEnc),
                edge.getReverse(accessEnc), width,
                edge.fetchWayGeometry(FetchMode.ALL));
    }



    private RoutingAlgorithm router(GraphHopper hopper) {
        Profile profile = hopper.getProfiles().iterator().next();
        if (useCH) {
            CHConfig chConfig = hopper.getCHPreparationHandler().getNodeBasedCHConfigs().get(0);
            Weighting weighting = chConfig.getWeighting();
            logger.info("CH algo, weighting: {}", weighting);
            return new RoutingRenderer.CHRoutingRenderer(
                    new QueryRoutingCHGraph(
                            hopper.getGraphHopperStorage().getRoutingCHGraph(chConfig.getName()),
                            QueryGraph.create(hopper.getGraphHopperStorage(),
                                    fromRes, toRes)));
        } else {
            Weighting weighting = hopper.createWeighting(profile, new PMap());
            final PrepareLandmarks preparation = hopper.getLMPreparationHandler().getPreparation(profile.getName());
            RoutingAlgorithmFactory algoFactory = (g, opts) -> {
                RoutingAlgorithm algo = preparation.getRoutingAlgorithmFactory().createAlgo(g, opts);
                return routerRendered(g, opts, algo);
            };
            AlgorithmOptions algoOpts = new AlgorithmOptions(Algorithms.ASTAR_BI, weighting);
            logger.info("algoOpts:{}, weighting: {}", algoOpts, weighting);
            QueryGraph qGraph = QueryGraph.create(graph, fromRes, toRes);
            return algoFactory.createAlgo(qGraph, algoOpts);
        }
    }

    private static RoutingAlgorithm routerRendered(Graph g, AlgorithmOptions opts, RoutingAlgorithm algo) {
        final Weighting w = opts.getWeighting();
        final TraversalMode t = opts.getTraversalMode();

        if (algo instanceof AStarBidirection) {
            return new RoutingRenderer.AStarBiRoutingRenderer(g, w, t).setApproximation(((AStarBidirection) algo).getApproximation());
        } else if (algo instanceof AStar) {
            return new RoutingRenderer.AStarRoutingRenderer(g, w, t);
        } else if (algo instanceof DijkstraBidirectionRef) {
            return new RoutingRenderer.DijkstraBidirectionRoutingRenderer(g, w, t);
        } else if (algo instanceof Dijkstra) {
            return new RoutingRenderer.DijkstraSimpleRoutingRenderer(g, w, t);
        } else
            throw new UnsupportedOperationException();
    }




    private void position(MouseEvent e) {
//        latLon = mg.getLat(e.getY()) + "," + mg.getLon(e.getX());
//        infoPanel.repaint();
        currentPosX = e.getX();
        currentPosY = e.getY();
    }

//    void repaintPaths() {
//        repaint(false, true);
//    }

    private void repaint() {

    }

    public void window(int w, int h) {
        new JOGL(new SpaceGraph3D() {
            @Override
            protected DynamicsWorld physics() {

                // the maximum size of the collision world. Make sure objects stay
                // within these boundaries
                // Don't make the world AABB size too large, it will harm simulation
                // quality and performance
                Vector3f worldAabbMin = new Vector3f(-10000, -10000, -10000),
                         worldAabbMax = new Vector3f(10000, 10000, 10000);
                int maxProxies = 1024;

                BroadphaseInterface b =
                    new AxisSweep3(worldAabbMin, worldAabbMax, maxProxies);
                    //new SimpleBroadphase(maxProxies);

                DynamicsWorld w = new DiscreteDynamicsWorld(b);

                w.localCreateRigidBody(1, new Transform(), new BoxShape(new Vector3f(1,1,1)));
                return w;
            }
        }, w, h);
    }


    static class GraphicsWrapper {
        private final Logger logger = LoggerFactory.getLogger(getClass());
        private final NodeAccess na;
        private double scaleX;
        private double scaleY;
        private double offsetX;
        private double offsetY;
        private BBox bounds = new BBox(-180, 180, -90, 90);

        GraphicsWrapper(Graph g) {
            this.na = g.getNodeAccess();
            BBox b = g.getBounds();
            scaleX = scaleY = 0.002 * (b.maxLat - b.minLat);
            offsetY = b.maxLat - 90;
            offsetX = -b.minLon;
        }


        private Path plotPath(Path tmpPath, Graphics2D g2, int w) {
            if (!tmpPath.isFound()) {
                logger.info("nothing found {}", w);
                return tmpPath;
            }


            IntIndexedContainer nodes = tmpPath.calcNodes();
            if (plotNodes) {
                final int n = nodes.size();
                for (int i = 0; i < n; i++) {
                    plotNodeName(g2, nodes.get(i));
                }
            }
            PointList list = tmpPath.calcPoints();
            final int p = list.getSize();
            double prevLat = Double.NaN;
            double prevLon = Double.NaN;
            for (int i = 0; i < p; i++) {
                double lat = list.getLatitude(i);
                double lon = list.getLongitude(i);
                if (!Double.isNaN(prevLat)) {
                    plotEdge(g2, prevLat, prevLon, lat, lon, w);
                } else {
                    plot(g2, lat, lon, w);
                }
                prevLat = lat;
                prevLon = lon;
            }
            logger.info("dist:{}, path points({})", tmpPath.getDistance(), p);
            return tmpPath;
        }
        void plotNodeName(Graphics2D g2, int node) {
            double lat = na.getLatitude(node);
            double lon = na.getLongitude(node);
            plotText(g2, lat, lon, String.valueOf(node));
        }

        private void plotWayGeometry(Graphics2D g2, boolean fwd, boolean bwd, float width, PointList pl) {
            g2.setStroke(new BasicStroke(width));
            final int n = pl.size();
            for (int s = 1; s < n; s++) {
                final double lat1 = pl.getLatitude(s - 1);
                final double lon1 = pl.getLongitude(s - 1);
                final double lat2 = pl.getLatitude(s);
                final double lon2 = pl.getLongitude(s);
                if (fwd && !bwd) {
                    plotDirectedEdge(g2, lat1, lon1, lat2, lon2, -1);
                } else {
                    plotEdge(g2, lat1, lon1, lat2, lon2, -1);
                }
            }
        }

        void plotText(Graphics2D g2, double lat, double lon, String text) {
            g2.drawString(text, (int) getX(lon) + 5, (int) getY(lat) + 5);
        }

        void plotDirectedEdge(Graphics2D g2, double lat, double lon, double lat2, double lon2, float width) {

            if (width > 0)
                g2.setStroke(new BasicStroke(width));

            int startLon = (int) Math.round(getX(lon));
            int startLat = (int) Math.round(getY(lat));
            int destLon = (int) Math.round(getX(lon2));
            int destLat = (int) Math.round(getY(lat2));

            g2.drawLine(startLon, startLat, destLon, destLat);

            // only for deep zoom show direction
            if (scaleX < 0.0001) {
                g2.setStroke(new BasicStroke(3));
                Path2D.Float path = new Path2D.Float();
                path.moveTo(destLon, destLat);
                path.lineTo(destLon + 6, destLat - 2);
                path.lineTo(destLon + 6, destLat + 2);
                path.lineTo(destLon, destLat);

                AffineTransform at = new AffineTransform();
                double angle = Math.atan2(lat2 - lat, lon2 - lon);
                at.rotate(-angle + Math.PI, destLon, destLat);
                path.transform(at);
                g2.draw(path);
            }
        }

        void plotEdge(Graphics2D g2, double lat, double lon, double lat2, double lon2, float width) {
            if (width > 0)
                g2.setStroke(new BasicStroke(width));
            g2.drawLine((int) Math.round(getX(lon)), (int) Math.round(getY(lat)), (int) Math.round(getX(lon2)), (int) Math.round(getY(lat2)));
        }

        public void plotEdge(Graphics2D g2, double lat, double lon, double lat2, double lon2) {
            plotEdge(g2, lat, lon, lat2, lon2, 1);
        }

        double getX(double lon) {
            return (lon + offsetX) / scaleX;
        }

        double getY(double lat) {
            return (90 - lat + offsetY) / scaleY;
        }

        double getLon(int x) {
            return x * scaleX - offsetX;
        }

        double getLat(int y) {
            return 90 - (y * scaleY - offsetY);
        }

        public void plotNode(Graphics2D g2, int loc, Color c) {
            plotNode(g2, loc, c, 4);
        }


        void plotNode(Graphics2D g2, int loc, Color c, int size) {
            plotNode(g2, loc, c, size, "");
        }

        void plotNode(Graphics2D g2, int loc, Color c, int size, String text) {
            plotNode(g2, na, loc, c, size, "");
        }

        void plotNode(Graphics2D g2, NodeAccess na, int loc, Color c, int size, String text) {
            double lat = na.getLatitude(loc);
            if (lat < bounds.minLat || lat > bounds.maxLat)
                return;

            double lon = na.getLongitude(loc);
            if (lon < bounds.minLon || lon > bounds.maxLon)
                return;

            Color old = g2.getColor();
            g2.setColor(c);
            plot(g2, lat, lon, size);
            g2.setColor(old);
        }

        void plot(Graphics2D g2, double lat, double lon, int width) {
            g2.fillOval((int) Math.round(getX(lon)), (int) Math.round(getY(lat)),
                    width, width);
        }

        void scale(int x, int y, boolean zoomIn) {
            double tmpFactor = 0.5f;
            if (!zoomIn) {
                tmpFactor = 2;
            }

            double oldScaleX = scaleX;
            double oldScaleY = scaleY;
            double resX = scaleX * tmpFactor;
            if (resX > 0) {
                scaleX = resX;
            }

            double resY = scaleY * tmpFactor;
            if (resY > 0) {
                scaleY = resY;
            }

            // respect mouse x,y when scaling
            // TODO minor bug: compute difference of lat,lon position for mouse before and after scaling
            if (zoomIn) {
                offsetX -= (offsetX + x) * scaleX;
                offsetY -= (offsetY + y) * scaleY;
            } else {
                offsetX += x * oldScaleX;
                offsetY += y * oldScaleY;
            }

            logger.info("mouse wheel moved => repaint. zoomIn:{} {},{} {},{}", zoomIn, offsetX, offsetY, scaleX, scaleY);
        }

        void setNewOffset(int offX, int offY) {
            offsetX += offX * scaleX;
            offsetY += offY * scaleY;
        }

        BBox setBounds(int minX, int maxX, int minY, int maxY) {
            return bounds = new BBox(getLon(minX), getLon(maxX), getLat(maxY), getLat(minY));
        }
    }
}
