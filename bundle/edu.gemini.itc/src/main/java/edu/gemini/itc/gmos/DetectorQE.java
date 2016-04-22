package edu.gemini.itc.gmos;

import edu.gemini.itc.base.Detector;
import edu.gemini.itc.base.ITCConstants;
import edu.gemini.itc.shared.GmosParameters;
import edu.gemini.itc.shared.ObservationDetails;

/**
 * Created by spakzad on 4/6/16.
 * Edited on 4/21/16.
 */
public abstract class DetectorQE extends Gmos {

    private final int DETECTOR_PIXELS = 6218;

    /**
     * Related files will be in this subdir of lib
     */
    public static final String INSTR_DIR = "gmos";

    protected static Detector _detector;
    protected static GmosParameters gp;
    protected ObservationDetails odp;

    public DetectorQE(GmosParameters gp, ObservationDetails odp, String FILENAME, int detectorCcdIndex) {
        super(gp, odp, FILENAME, detectorCcdIndex);
        _detectorCcdIndex = detectorCcdIndex;
    }

    protected abstract String[] getCcdFiles();
    protected abstract String[] getCcdNames();

    int _detectorCcdIndex = 0; // 0, 1, or 2 when there are multiple CCDs in the detector

    public void DetectorQEAdd() {

        switch (gp.ccdType()) {
            // E2V, site dependent
            case E2V:
                switch (gp.site()) {
                    // E2V for GN: gmos_n_E2V4290DDmulti3.dat      => EEV DD array
                    case GN:
                        _detector = new Detector(getDirectory() + "/", getPrefix(), "E2V4290DDmulti3", "EEV DD array");
                        _detector.setDetectorPixels(DETECTOR_PIXELS);
                        break;
                    // E2V for GS: gmos_n_cdd_red.dat              => EEV legacy
                    case GS:
                        _detector = new Detector(getDirectory() + "/", getPrefix(), "ccd_red", "EEV legacy array");
                        _detector.setDetectorPixels(DETECTOR_PIXELS);
                        break;
                    default:
                        throw new Error("invalid site");
                }
                break;
            // Hamamatsu, both sites: gmos_n_CCD-{R,G,B}.dat        =>  Hamamatsu (R,G,B)
            case HAMAMATSU:
                String fileName = getCcdFiles()[_detectorCcdIndex];
                String name = getCcdNames()[_detectorCcdIndex];
                _detector = new Detector(getDirectory() + "/", getPrefix(), fileName, "Hamamatsu array", name);
                _detector.setDetectorPixels(DETECTOR_PIXELS);
            default:
                throw new Error("invalid ccd type");
        }

        addComponent(_detector);
    }

    protected abstract String getPrefix();

    public String getDirectory() {
        return ITCConstants.LIB + "/" + INSTR_DIR;
    }

}
