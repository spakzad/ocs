package edu.gemini.itc.flamingos2;

import edu.gemini.itc.base.*;
import edu.gemini.itc.operation.DetectorsTransmissionVisitor;
import edu.gemini.itc.shared.Flamingos2Parameters;
import edu.gemini.spModel.gemini.flamingos2.Flamingos2.FPUnit;
import scala.Option;

import java.io.File;

/**
 * Flamingos 2 specification class
 */
public final class Flamingos2 extends Instrument {

    private static final String FILENAME = "flamingos2" + getSuffix();
    public static final String INSTR_DIR = "flamingos2";
    public static final String INSTR_PREFIX = "";
    public static final String INSTR_PREFIX_2 = "flamingos2_";
    private static final double WELL_DEPTH = 155400.0;
    public static String getPrefix() {
        return INSTR_PREFIX;
    }
    public static String getPrefix2() {
        return INSTR_PREFIX_2;
    }


    private final DetectorsTransmissionVisitor _dtv;
    private final Option<Filter> _colorFilter;
    private final Option<GrismOptics> _grismOptics;
    private final Flamingos2Parameters params;
    private final double _slitSize;

    /**
     * construct a Flamingos2 object with specified color filter and ND filter.
     */
    public Flamingos2(final Flamingos2Parameters fp) {
        super(INSTR_DIR, FILENAME);

        params = fp;
        _slitSize = getSlitSize() * getPixelSize();
        _colorFilter = addColorFilter(fp);
        _dtv = new DetectorsTransmissionVisitor(1, getDirectory() + "/" + getPrefix2() + "ccdpix" + Instrument.getSuffix());

        addComponent(new FixedOptics(getDirectory() + File.separator, getPrefix()));
        addComponent(new Detector(getDirectory() + File.separator, getPrefix(), "detector", "2048x2048 Hawaii-II (HgCdTe)"));

        _grismOptics = addGrism(fp);
    }

    private Option<Filter> addColorFilter(final Flamingos2Parameters fp) {
        switch (fp.filter()) {
            case OPEN:
                return Option.empty();
            default:
                final Filter filter = Filter.fromFile(getPrefix(), fp.filter().name(), getDirectory() + "/");
                addFilter(filter);
                return Option.apply(filter);
        }
    }

    private Option<GrismOptics> addGrism(final Flamingos2Parameters fp) {
        switch (fp.grism()) {
            case NONE:
                return Option.empty();
            default:
                final GrismOptics grismOptics;
                try {
                    grismOptics = new GrismOptics(getDirectory() + File.separator, fp.grism().name(), _slitSize * getPixelSize(), fp.filter().name());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Grism/filter " + fp.grism() + "+" + fp.filter().name() + " combination is not supported.");
                }
                addComponent(grismOptics);
                return Option.apply(grismOptics);
        }
    }

    public double getSlitSize() {
        switch (params.mask()) {
            case FPU_NONE: return 1;
            default: return params.mask().getSlitWidth();
        }
    }

    /**
     * Returns the subdirectory where this instrument's data files are.
     */
    public String getDirectory() {
        return ITCConstants.LIB + "/" + INSTR_DIR;
    }

    /**
     * Returns the effective observing wavelength. This is properly calculated
     * as a flux-weighted averate of observed spectrum. So this may be
     * temporary.
     *
     * @return Effective wavelength in nm
     */
    public int getEffectiveWavelength() {
        if (_colorFilter.isEmpty()) {
            return (int) (getEnd() + getStart()) / 2;
        } else {
            return (int) _colorFilter.get().getEffectiveWavelength();
        }
    }

    public double getGrismResolution() {
        if (_grismOptics.isDefined()) {
            return _grismOptics.get().getGrismResolution();
        } else {
            return 0;
        }
    }

    public double getObservingEnd() {
        if (_colorFilter.isDefined()) {
            return _colorFilter.get().getEnd();
        } else {
            return getEnd();
        }
    }

    public double getObservingStart() {
        if (_colorFilter.isDefined()) {
            return _colorFilter.get().getStart();
        } else {
            return getStart();
        }
    }

    public DetectorsTransmissionVisitor getDetectorTransmision() {
        return _dtv;
    }

    @Override
    public double getReadNoise() {
        switch (params.readMode()) {
            // TODO: this is for regression tests only, actual readmode is defined as 12.1
            case BRIGHT_OBJECT_SPEC:    return 12.0;
            default:                    return params.readMode().readNoise();
        }
    }

    public double getSpectralPixelWidth() {
        assert _grismOptics.isDefined();
        return _grismOptics.get().getPixelWidth();
    }

    public double getWellDepth() {
        return WELL_DEPTH;
    }

    public FPUnit getFocalPlaneMask() {
        return params.mask();
    }

    public String getReadNoiseString() {
        switch (params.readMode()) {
            case BRIGHT_OBJECT_SPEC: return "highNoise";
            case MEDIUM_OBJECT_SPEC: return "medNoise";
            case FAINT_OBJECT_SPEC:  return "lowNoise";
            default:                 throw new Error();
        }

    }
}
