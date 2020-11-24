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

import com.graphhopper.routing.*;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
interface RoutingRenderer {

    /**
     * @author Peter Karich
     */
    class AStarRoutingRenderer extends AStar implements RoutingRenderer {

        private final NodeAccess na;

        public AStarRoutingRenderer(Graph graph, Weighting type, TraversalMode tMode) {
            super(graph, type, tMode);
            na = graph.getNodeAccess();
        }


        @Override
        public void updateBestPath(EdgeIteratorState es, SPTEntry bestEE, int currLoc) {
//            if (g2 != null) {
//                mg.plotEdge(g2, na.getLat(bestEE.parent.adjNode), na.getLon(bestEE.parent.adjNode), na.getLat(currLoc), na.getLon(currLoc), .8f);
//            }
            super.updateBestPath(es, bestEE, currLoc);
        }
    }

    /**
     * @author Peter Karich
     */
    class AStarBiRoutingRenderer extends AStarBidirection implements RoutingRenderer {


        public AStarBiRoutingRenderer(Graph graph, Weighting type, TraversalMode tMode) {
            super(graph, type, tMode);
        }


        @Override
        public void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
//            if (g2 != null) {
//                mg.plotNode(g2, traversalId, Color.YELLOW);
//            }
            super.updateBestPath(edgeWeight, entry, origEdgeId, traversalId, reverse);
        }

        @Override
        public String toString() {
            return "debugui|" + super.toString();
        }
    }

    /**
     * @author Peter Karich
     */
    class DijkstraBidirectionRoutingRenderer extends DijkstraBidirectionRef implements RoutingRenderer {

        private final NodeAccess na;

        public DijkstraBidirectionRoutingRenderer(Graph graph, Weighting type, TraversalMode tMode) {
            super(graph, type, tMode);
            na = graph.getNodeAccess();
        }


        @Override
        public void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
//            if (g2 != null) {
//                mg.plotEdge(g2, na.getLat(entry.parent.adjNode), na.getLon(entry.parent.adjNode), na.getLat(entry.adjNode), na.getLon(entry.adjNode), .8f);
//            }
            // System.out.println("new node:" + currLoc);
            super.updateBestPath(edgeWeight, entry, origEdgeId, traversalId, reverse);
        }
    }

    /**
     * @author Peter Karich
     */
    class DijkstraSimpleRoutingRenderer extends Dijkstra implements RoutingRenderer {


        public DijkstraSimpleRoutingRenderer(Graph graph, Weighting weighting, TraversalMode tMode) {
            super(graph, weighting, tMode);
        }


        @Override
        public void updateBestPath(EdgeIteratorState es, SPTEntry bestEE, int currLoc) {
//            if (g2 != null) {
//                mg.plotNode(g2, currLoc, Color.YELLOW);
//            }
            super.updateBestPath(es, bestEE, currLoc);
        }
    }

    class CHRoutingRenderer extends DijkstraBidirectionCH implements RoutingRenderer {


        public CHRoutingRenderer(RoutingCHGraph graph) {
            super(graph);
        }


        @Override
        public void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
//            if (g2 != null)
//                mg.plotNode(g2, traversalId, Color.YELLOW, 6);

            super.updateBestPath(edgeWeight, entry, origEdgeId, traversalId, reverse);
        }
    }

}
