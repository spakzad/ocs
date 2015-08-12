package edu.gemini.itc.flamingos2;

import edu.gemini.itc.base.*;
import edu.gemini.itc.operation.*;
import edu.gemini.itc.shared.*;
import edu.gemini.spModel.core.Site;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;

/**
 * This class performs the calculations for Flamingos 2 used for imaging.
 */
public final class Flamingos2Recipe implements ImagingRecipe, SpectroscopyRecipe {

    private final Flamingos2Parameters _flamingos2Parameters;
    private final ObservingConditions _obsConditionParameters;
    private final ObservationDetails _obsDetailParameters;
    private final SourceDefinition _sdParameters;
    private final TelescopeDetails _telescope;

    /**
     * Constructs an Flamingos 2 object given the parameters. Useful for
     * testing.
     */
    public Flamingos2Recipe(final SourceDefinition sdParameters,
                            final ObservationDetails obsDetailParameters,
                            final ObservingConditions obsConditionParameters,
                            final Flamingos2Parameters flamingos2Parameters,
                            final TelescopeDetails telescope) {
        _sdParameters = sdParameters;
        _obsDetailParameters = obsDetailParameters;
        _obsConditionParameters = obsConditionParameters;
        _flamingos2Parameters = flamingos2Parameters;
        _telescope = telescope;

        validateInputParameters();
    }

    /**
     * Check input parameters for consistency
     */
    private void validateInputParameters() {
        if (_obsDetailParameters.getMethod().isSpectroscopy()) {
            switch (_flamingos2Parameters.grism()) {
                case NONE:          throw new IllegalArgumentException("In spectroscopy mode, a grism must be selected");
            }
            switch (_flamingos2Parameters.mask()) {
                case FPU_NONE:      throw new IllegalArgumentException("In spectroscopy mode, a FP must be selected");
                // NOTE: Currently it is not possible to set a slit width for custom masks for F2 in the OT
                case CUSTOM_MASK:   throw new IllegalArgumentException("Custom masks with unknown slit widths are not supported");
            }
        }

        // some general validations
        Validation.validate(_obsDetailParameters, _sdParameters);
    }

    public Tuple2<ItcSpectroscopyResult, SpectroscopyResult> calculateSpectroscopy() {
        final Flamingos2 instrument = new Flamingos2(_flamingos2Parameters);
        final SpectroscopyResult r = calculateSpectroscopy(instrument);
        final List<SpcChartData> dataSets = new ArrayList<SpcChartData>() {{
            add(Recipe$.MODULE$.createSignalChart(r));
            add(Recipe$.MODULE$.createS2NChart(r));
        }};
        final List<SpcDataFile> dataFiles = new ArrayList<SpcDataFile>() {{
            add(new SpcDataFile(SignalData.instance(),     r.specS2N()[0].getSignalSpectrum().printSpecAsString()));
            add(new SpcDataFile(BackgroundData.instance(), r.specS2N()[0].getBackgroundSpectrum().printSpecAsString()));
            add(new SpcDataFile(SingleS2NData.instance(),  r.specS2N()[0].getExpS2NSpectrum().printSpecAsString()));
            add(new SpcDataFile(FinalS2NData.instance(),   r.specS2N()[0].getFinalS2NSpectrum().printSpecAsString()));
        }};
        return new Tuple2<>(ItcSpectroscopyResult.apply(dataSets, dataFiles, new ArrayList<>()), r);
    }

    public ImagingResult calculateImaging() {
        final Flamingos2 instrument = new Flamingos2(_flamingos2Parameters);
        return calculateImaging(instrument);
    }

    private SpectroscopyResult calculateSpectroscopy(final Flamingos2 instrument) {
        // Start of morphology section of ITC

        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio. There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification
        final SEDFactory.SourceResult src = SEDFactory.calculate(instrument, Site.GS, ITCConstants.NEAR_IR, _sdParameters, _obsConditionParameters, _telescope);

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();
        final double im_qual = IQcalc.getImageQuality();

        // Calculate Source fraction
        final SourceFraction SFcalc = SourceFractionFactory.calculate(_sdParameters, _obsDetailParameters, instrument, im_qual);

        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.

        final double pixel_size = instrument.getPixelSize();
        final SpecS2NLargeSlitVisitor specS2N;
        final SlitThroughput st;

        if (!_obsDetailParameters.isAutoAperture()) {
            st = new SlitThroughput(im_qual, _obsDetailParameters.getApertureDiameter(), pixel_size, instrument.getSlitSize() * pixel_size);
        } else {
            st = new SlitThroughput(im_qual, pixel_size, instrument.getSlitSize() * pixel_size);
        }

        double ap_diam = st.getSpatialPix();
        double spec_source_frac = st.getSlitThroughput();

        if (_sdParameters.isUniform()) {
            if (_obsDetailParameters.isAutoAperture()) {
                ap_diam = new Double(1 / (instrument.getSlitSize() * pixel_size) + 0.5).intValue();
                spec_source_frac = 1;
            } else {
                spec_source_frac = instrument.getSlitSize() * pixel_size * ap_diam * pixel_size;
            }
        }

        final double gratDispersion_nmppix = instrument.getSpectralPixelWidth();
        final double gratDispersion_nm = 0.5 / pixel_size * gratDispersion_nmppix;

        specS2N = new SpecS2NLargeSlitVisitor(instrument.getSlitSize() * pixel_size,
                pixel_size, instrument.getSpectralPixelWidth(),
                instrument.getObservingStart(),
                instrument.getObservingEnd(),
                gratDispersion_nm,
                gratDispersion_nmppix,
                instrument.getGrismResolution(), spec_source_frac, im_qual,
                ap_diam,
                _obsDetailParameters.getNumExposures(),
                _obsDetailParameters.getSourceFraction(),
                _obsDetailParameters.getExposureTime(),
                instrument.getDarkCurrent(),
                instrument.getReadNoise(),
                _obsDetailParameters.getSkyApertureDiameter());

        specS2N.setDetectorTransmission(instrument.getDetectorTransmision());
        specS2N.setSourceSpectrum(src.sed);
        specS2N.setBackgroundSpectrum(src.sky);
        specS2N.setSpecHaloSourceFraction(0.0);
        src.sed.accept(specS2N);

        final SpecS2NLargeSlitVisitor[] specS2Narr = new SpecS2NLargeSlitVisitor[1];
        specS2Narr[0] = specS2N;

        final Parameters p = new Parameters(_sdParameters, _obsDetailParameters, _obsConditionParameters, _telescope);
        return SpectroscopyResult$.MODULE$.apply(p, instrument, SFcalc, IQcalc, specS2Narr, st);
    }

    private ImagingResult calculateImaging(final Flamingos2 instrument) {

        // Start of morphology section of ITC

        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio. There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification
        final SEDFactory.SourceResult src = SEDFactory.calculate(instrument, Site.GS, ITCConstants.NEAR_IR, _sdParameters, _obsConditionParameters, _telescope);

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();
        final double im_qual = IQcalc.getImageQuality();

        // Calculate Source fraction
        final SourceFraction SFcalc = SourceFractionFactory.calculate(_sdParameters, _obsDetailParameters, instrument, im_qual);

        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.

        // Get the summed source and sky
        final VisitableSampledSpectrum sed = src.sed;
        final VisitableSampledSpectrum sky = src.sky;
        final double sed_integral = sed.getIntegral();
        final double sky_integral = sky.getIntegral();

        // Calculate the Peak Pixel Flux
        final double peak_pixel_count = PeakPixelFlux.calculate(instrument, _sdParameters, _obsDetailParameters, SFcalc, im_qual, sed_integral, sky_integral);

        // Calculate the Signal to Noise
        final ImagingS2NCalculatable IS2Ncalc = ImagingS2NCalculationFactory.getCalculationInstance(_obsDetailParameters, instrument, SFcalc, sed_integral, sky_integral);
        IS2Ncalc.calculate();

        final Parameters p = new Parameters(_sdParameters, _obsDetailParameters, _obsConditionParameters, _telescope);
        final List<ItcWarning> warnings = warningsForImaging(instrument, peak_pixel_count);
        return ImagingResult.apply(p, instrument, IQcalc, SFcalc, peak_pixel_count, IS2Ncalc, warnings);
    }

    // TODO: some of these warnings are similar for different instruments and could be calculated in a central place
    private List<ItcWarning> warningsForImaging(final Flamingos2 instrument, final double peakPixelCount) {
        final double wellLimit = 0.8 * instrument.getWellDepth();
        return new ArrayList<ItcWarning>() {{
            if      (peakPixelCount > wellLimit) add(new ItcWarning("Warning: peak pixel exceeds 80% of the well depth and may be saturated"));
            else if (peakPixelCount > 80000)     add(new ItcWarning(String.format("Warning: peak pixel is %.0f %% of the linearity limit of 98,000 e- (linearity is better than 0.5%% below 98,000 e-).", peakPixelCount/980)));
        }};
    }


}
