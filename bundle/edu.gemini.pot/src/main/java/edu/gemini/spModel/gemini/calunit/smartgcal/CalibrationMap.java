package edu.gemini.spModel.gemini.calunit.smartgcal;

import java.io.Serializable;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Base interface for all calibration maps.
 */
public interface CalibrationMap extends Serializable {

    /**
     * Creates the set of all keys that cover the given properties (this includes expansion of wildcards etc.).
     * @param properties
     * @return
     */
    Set<ConfigurationKey> createConfig(Properties properties);

    Calibration createCalibration(Properties properties);

    ConfigurationKey.Values[] getKeyValueNames();

    ConfigurationKey.Values[] getCalibrationValueNames();

    Calibration put(ConfigurationKey key, Properties properties, Calibration calibration);

    /**
     * Gets a sequence of calibrations to be taken for the given instrument configuration (key).
     * @param key
     * @return
     */
    List<Calibration> get(ConfigurationKey key);

    /**
     * Gets a sequence of calibrations to be taken for the given instrument configuration (key) and wavelength.
     * @param key
     * @param centralWavelength
     * @return
     */
    List<Calibration> get(ConfigurationKey key, Double centralWavelength);

    Version getVersion();

}
