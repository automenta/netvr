package netvr;

import gov.nasa.worldwind.*;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.Message;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.layers.Earth.OSMMapnikLayer;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.SkyGradientLayer;
import gov.nasa.worldwind.layers.StarsLayer;
import gov.nasa.worldwind.util.Logging;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;

public class WorldWindOSM {

    public WorldWindOSM() {
    }

    static class OSMModel extends WWObjectImpl implements Model {
        private Globe globe;
        private LayerList layers;
        private boolean showWireframeInterior = false;
        private boolean showWireframeExterior = true;
        private boolean showTessellationBoundingVolumes = true;

        public OSMModel() {
            this.setGlobe((Globe)
                WorldWind.createComponent(
                    Configuration.getStringValue(AVKey.GLOBE_CLASS_NAME)));

            // Look for the old-style, property-based layer configuration first. If not found then use the new-style
            // configuration.
            LayerList layers = new LayerList();
            layers.add(new StarsLayer());
            layers.add(new SkyGradientLayer());
            //layers.add(new BMNGOneImage());
            layers.add(new OSMMapnikLayer());
            this.setLayers(layers);
        }

        public Globe getGlobe() {
            return this.globe;
        }

        public void setGlobe(Globe globe) {
            // don't raise an exception if globe == null. In that case, we are disassociating the model from any globe

            //remove property change listener "this" from the current globe.
            if (this.globe != null)
                this.globe.removePropertyChangeListener(this);

            // if the new globe is not null, add "this" as a property change listener.
            if (globe != null)
                globe.addPropertyChangeListener(this);

            Globe old = this.globe;
            this.globe = globe;
            this.firePropertyChange(AVKey.GLOBE, old, this.globe);
        }

        /**
         * {@inheritDoc}
         */
        public LayerList getLayers() {
            return this.layers;
        }

        /**
         * {@inheritDoc}
         */
        public void setLayers(LayerList layers) {
            // don't raise an exception if layers == null. In that case, we are disassociating the model from any layer set

            if (this.layers != null)
                this.layers.removePropertyChangeListener(this);
            if (layers != null)
                layers.addPropertyChangeListener(this);

            LayerList old = this.layers;
            this.layers = layers;
            this.firePropertyChange(AVKey.LAYERS, old, this.layers);
        }

        /**
         * {@inheritDoc}
         */
        public boolean isShowWireframeInterior() {
            return this.showWireframeInterior;
        }

        /**
         * {@inheritDoc}
         */
        public void setShowWireframeInterior(boolean show) {
            this.showWireframeInterior = show;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isShowWireframeExterior() {
            return this.showWireframeExterior;
        }

        /**
         * {@inheritDoc}
         */
        public void setShowWireframeExterior(boolean show) {
            this.showWireframeExterior = show;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isShowTessellationBoundingVolumes() {
            return showTessellationBoundingVolumes;
        }

        /**
         * {@inheritDoc}
         */
        public void setShowTessellationBoundingVolumes(boolean showTessellationBoundingVolumes) {
            this.showTessellationBoundingVolumes = showTessellationBoundingVolumes;
        }

        
        public Extent getExtent() {
            // See if the layers have it.
            LayerList layers = OSMModel.this.getLayers();
            if (layers != null) {
                for (Object layer1 : layers) {
                    Layer layer = (Layer) layer1;
                    Extent e = (Extent) layer.getValue(AVKey.EXTENT);
                    if (e != null)
                        return e;
                }
            }

            // See if the Globe has it.
            Globe globe = this.getGlobe();
            if (globe != null) {
                return globe.getExtent();
            }

            return null;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation forwards the message each layer in the model.
         *
         * @param msg The message that was received.
         */
        @Override
        public void onMessage(Message msg) {
            if (this.getLayers() != null) {
                for (Layer layer : this.getLayers()) {
                    try {
                        if (layer != null) {
                            layer.onMessage(msg);
                        }
                    }
                    catch (Exception e) {
                        String message = Logging.getMessage("generic.ExceptionInvokingMessageListener");
                        Logging.logger().log(Level.SEVERE, message, e);
                        // Don't abort; continue on to the next layer.
                    }
                }
            }
        }
    }
    
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            JFrame f = new JFrame();

            WorldWindowGLCanvas wwd = new WorldWindowGLCanvas();
            wwd.setPreferredSize(new Dimension(1000, 800));
            f.getContentPane().add(wwd, BorderLayout.CENTER);

            wwd.setModel(new OSMModel());

            f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);

        });
    }

}
