package edu.gemini.itc.gmos;

import edu.gemini.itc.base.BinningProvider;
import edu.gemini.itc.base.Detector;
import edu.gemini.itc.base.Filter;
import edu.gemini.itc.base.FixedOptics;
import edu.gemini.itc.base.Instrument;
import edu.gemini.itc.base.SpectroscopyInstrument;
import edu.gemini.itc.operation.DetectorsTransmissionVisitor;
import edu.gemini.itc.shared.GmosParameters;
import edu.gemini.itc.shared.IfuMethod;
import edu.gemini.itc.shared.IfuRadial;
import edu.gemini.itc.shared.IfuSingle;
import edu.gemini.itc.shared.ObservationDetails;
import edu.gemini.spModel.gemini.gmos.GmosNorthType;
import edu.gemini.spModel.gemini.gmos.GmosSouthType;
import scala.Option;

/**
 * Created by spakzad on 4/6/16.
 * Edited on 4/21/16.
 */

public abstract class DetectorQE extends Instrument  implements BinningProvider, SpectroscopyInstrument {

    private final int DETECTOR_PIXELS = 6218;

    /**
     * Related files will be in this subdir of lib
     */
    public static final String INSTR_DIR = "gmos";

    protected Filter _Filter;
    protected Detector _detector;
    protected final GmosParameters gp;
    protected final ObservationDetails odp;
    protected double _sampling;
    protected abstract String[] getCcdFiles();
    protected abstract String[] getCcdNames();
    protected IFUComponent _IFU;
    protected GmosGratingOptics _gratingOptics;
    protected DetectorsTransmissionVisitor _dtv;

    private int _detectorCcdIndex = 0; // 0, 1, or 2 when there are multiple CCDs in the detector

    public DetectorQE(final GmosParameters gp, final ObservationDetails odp, final String FILENAME, final int detectorCcdIndex) {
        super(gp.site(), Bands.VISIBLE, INSTR_DIR, FILENAME);

        this.odp    = odp;
        this.gp     = gp;

        _detectorCcdIndex = detectorCcdIndex;

        _sampling = super.getSampling();

        // TODO: filter is not yet defined, need to work with filter from gp, clean this up
        if (!gp.filter().equals(GmosNorthType.FilterNorth.NONE) && !gp.filter().equals(GmosSouthType.FilterSouth.NONE)) {
            _Filter = Filter.fromWLFile(getPrefix(), gp.filter().name(), getDirectory() + "/");
            addFilter(_Filter);
        }

        FixedOptics _fixedOptics = new FixedOptics(getDirectory() + "/", getPrefix());
        addComponent(_fixedOptics);

        //Choose correct CCD QE curve
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
                String fileName = getCcdFiles()[detectorCcdIndex];
                String name = getCcdNames()[detectorCcdIndex];
                _detector = new Detector(getDirectory() + "/", getPrefix(), fileName, "Hamamatsu array", name);
                _detector.setDetectorPixels(DETECTOR_PIXELS);
            default:
                throw new Error("invalid ccd type");
        }

        if (isIfuUsed() && getIfuMethod().isDefined()) {
            if (getIfuMethod().get() instanceof IfuSingle) {
                _IFU = new IFUComponent(getPrefix(), ((IfuSingle) getIfuMethod().get()).offset());
            } else if (getIfuMethod().get() instanceof IfuRadial) {
                final IfuRadial ifu = (IfuRadial) getIfuMethod().get();
                _IFU = new IFUComponent(getPrefix(), ifu.minOffset(), ifu.maxOffset());
            } else {
                throw new Error("invalid IFU type");
            }
            addComponent(_IFU);
        }


        // TODO: grating is not yet defined, need to work with grating from gp, clean this up
        if (!gp.grating().equals(GmosNorthType.DisperserNorth.MIRROR) && !gp.grating().equals(GmosSouthType.DisperserSouth.MIRROR)) {
            _gratingOptics = new GmosGratingOptics(getDirectory() + "/" + getPrefix(), gp.grating(), _detector,
                    gp.centralWavelength().toNanometers(),
                    _detector.getDetectorPixels(),
                    gp.spectralBinning());
            _sampling = _gratingOptics.dispersion();
            addDisperser(_gratingOptics);

            // we only need the detector transmission visitor for the spectroscopy case (i.e. if there is a grating)
            if (detectorCcdIndex == 0) {
                final double nmppx = _gratingOptics.dispersion();
                _dtv = new DetectorsTransmissionVisitor(gp, nmppx, getDirectory() + "/" + getPrefix() + "ccdpix" + Instrument.getSuffix());
            }
        }

    }

    protected void DetectorQEAdd() {
        addComponent(_detector);      // TODO: REL-2577:  Move line to AFTER any IFU wavelength shifts.
    }

    protected abstract String getPrefix();

    public boolean isIfuUsed() {
        return gp.fpMask().isIFU();
    }

    public Option<IfuMethod> getIfuMethod() {
        return (odp.analysisMethod() instanceof IfuMethod) ? Option.apply((IfuMethod) odp.analysisMethod()): Option.empty();
    }

}

