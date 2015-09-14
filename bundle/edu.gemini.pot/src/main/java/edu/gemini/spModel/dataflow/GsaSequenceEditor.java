package edu.gemini.spModel.dataflow;

import edu.gemini.pot.sp.ISPProgram;
import edu.gemini.spModel.config2.ItemKey;
import edu.gemini.spModel.data.config.*;
import edu.gemini.spModel.obsclass.ObsClass;
import static edu.gemini.spModel.seqcomp.SeqConfigNames.OBSERVE_CONFIG_NAME;
import static edu.gemini.spModel.seqcomp.SeqConfigNames.OBSERVE_KEY;

/**
 * Support for adding archive information to the sequence so that the seqexec
 * can use it to add release date and proprietary metadata header keys.
 */
public enum GsaSequenceEditor {
    instance;

    public static final String  PROPRIETARY_MD         = "proprietaryMd";
    public static final ItemKey PROPRIETARY_MD_KEY     = new ItemKey(OBSERVE_KEY, PROPRIETARY_MD);

    public static final String  PROPRIETARY_MONTHS     = "proprietaryMonths";
    public static final ItemKey PROPRIETARY_MONTHS_KEY = new ItemKey(OBSERVE_KEY, PROPRIETARY_MONTHS);

    public void addProprietaryMetadata(IConfig c, GsaAspect gsa) {
        c.putParameter(OBSERVE_CONFIG_NAME, DefaultParameter.getInstance(PROPRIETARY_MD, gsa.isHeaderPrivate()));
    }

    public void addProprietaryPeriod(IConfig c, GsaAspect gsa, ObsClass obsClass) {
        final int m = obsClass.shouldChargeProgram() ? gsa.getProprietaryMonths() : 0;
        c.putParameter(OBSERVE_CONFIG_NAME, DefaultParameter.getInstance(PROPRIETARY_MONTHS, m));
    }

    public void addProprietaryPeriod(IConfig c, ISPProgram prog, ObsClass obsClass) {
        addProprietaryPeriod(c, GsaAspect.lookup(prog), obsClass);
    }
}
