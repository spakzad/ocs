package edu.gemini.itc.shared;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import scala.Option;
import scala.collection.JavaConversions;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


public final class ITCChart {

    // ==========================================================================
    // Static

    // Define a set of colors for charts.
    // This are the same colors as used for Queue Visualization, maybe at some point we want
    // to make the QV color schematas available OT wide?
    private static final List<Color> Colors = new ArrayList<Color>() {{
        add(new Color(166,206,227));
        add(new Color(31,120,180));
        add(new Color(178,223,138));
        add(new Color(51,160,44));
        add(new Color(251,154,153));
        add(new Color(227,26,28));
        add(new Color(253,191,111));
        add(new Color(255,127,0));
        add(new Color(202,178,214));
        add(new Color(106,61,154));
        add(new Color(177,89,40));
        // == repeat colors one shade darker
        add(new Color(166, 206, 227).darker());
        add(new Color(31, 120, 180).darker());
        add(new Color(178, 223, 138).darker());
        add(new Color(51, 160, 44).darker());
        add(new Color(251, 154, 153).darker());
        add(new Color(227, 26, 28).darker());
        add(new Color(253, 191, 111).darker());
        add(new Color(255, 127, 0).darker());
        add(new Color(202, 178, 214).darker());
        add(new Color(106, 61, 154).darker());
        add(new Color(177, 89, 40).darker());
    }};

    // helper method to provide a pretty color by index
    public static Color colorByIndex(final int ix) {
        return Colors.get(ix % Colors.size());
    }

    // some specific colors, used for blue/green/red detectors (GMOS)
    public static Color LightBlue  = Colors.get(0);
    public static Color DarkBlue   = Colors.get(1);
    public static Color LightGreen = Colors.get(2);
    public static Color DarkGreen  = Colors.get(3);
    public static Color LightRed   = Colors.get(4);
    public static Color DarkRed    = Colors.get(5);

    public static ITCChart forSpcDataSet(final SpcChartData s, final PlottingDetails plotParams) {
        return new ITCChart(s, plotParams);
    }

    // ==========================================================================
    // Actual class stuff

    private final XYSeriesCollection seriesData = new XYSeriesCollection();
    private final JFreeChart chart;

    public ITCChart(final SpcChartData s, final PlottingDetails plotParams) {

        chart = ChartFactory.createXYLineChart(s.title(), s.xAxis().label(), s.yAxis().label(), this.seriesData, PlotOrientation.VERTICAL, true, false, false);
        chart.getLegend().setPosition(RectangleEdge.TOP);
        chart.setBackgroundPaint(Color.white);

        final XYPlot plot = chart.getXYPlot();
        plot.setOutlineVisible(false);
        plot.setBackgroundPaint(Color.white);
        plot.setRangeTickBandPaint(new Color(248, 248, 255));
        plot.setDomainGridlinePaint(Color.gray);
        plot.setRangeGridlinePaint(Color.gray);

        // invert domain axis if needed
        if (s.xAxis().inverted()) {
            final NumberAxis axis0 = new NumberAxis(s.xAxis().label());
            axis0.setInverted(true);
            plot.setDomainAxisLocation(0, AxisLocation.BOTTOM_OR_LEFT);
            plot.setDomainAxis(0, axis0);
            plot.mapDatasetToDomainAxis(0, 0);
        }

        double ifu2XBlocation = 0;
        double ifu2XRlocation = 0;
        double ifu2Ylocation  = 1.0;

        // scale xAxis - domain axis
        if (plotParams.getPlotLimits().equals(PlottingDetails.PlotLimits.USER)) {
            setDomainMinMax(plotParams.getPlotWaveL(), plotParams.getPlotWaveU());
        } else if (s.xAxis().range().isDefined()) {
            final ChartAxisRange range = s.xAxis().range().get();
            setDomainMinMax(range.start(), range.end());
        }

        // scale yAxis - range axis
        if (s.yAxis().range().isDefined()) {
            final ChartAxisRange range = s.xAxis().range().get();
            chart.getXYPlot().getRangeAxis().setRange(range.start(), range.end());
        }

        // additional axes as needed
        for (int i = 0; i < s.axes().size(); i++) {
            final ChartAxis  a = s.axes().apply(i);
            final NumberAxis axis = new NumberAxis(a.label());
            if (a.range().isDefined()) {
                // TODO: additional axes MUST have a range, while for x and y axis it is optional
                axis.setRange(a.range().get().start(), a.range().get().end());
            }
            plot.setDomainAxis(i + 1, axis);
            plot.setDomainAxisLocation(i + 1, AxisLocation.TOP_OR_LEFT);

            ifu2XRlocation = (s.xAxis().range().get().end() - s.xAxis().range().get().start()) * (1.0/4.0);
            ifu2XBlocation = (s.xAxis().range().get().end() - s.xAxis().range().get().start()) * (3.0/4.0);

            //ifu2Ylocation = this.seriesData.getSeries().size();
            //ifu2Ylocation = seriesData.getDomainUpperBound();
            //ifu2Ylocation = plotParams.getPlotLimits().equals()
            ifu2Ylocation  = chart.getXYPlot().getRangeAxis().getRange().getLength();                          /// = 1.05
            //ifu2Ylocation  = s.yAxis().range().get().end();                                                    /// = Didn't work
            //ifu2Ylocation  = s.yAxis().range().get().start();                                                  /// = Didn't work
            //ifu2Ylocation  = chart.getXYPlot().getRangeAxis().getRange().getCentralValue();                    /// = 0.525
            //ifu2Ylocation  = chart.getXYPlot().getRangeAxis().getRange().getUpperBound();                      /// = 1.05
            //ifu2Ylocation  = plot.getRangeCrosshairValue();                                                    /// =  0.0
            //ifu2Ylocation  = (s.yAxis().range().get().end() - s.yAxis().range().get().start()) * (9.0/10.0);   /// = Didn't work

            final XYTextAnnotation annotation1 = new XYTextAnnotation("IFU-B", ifu2XBlocation, ifu2Ylocation);       // TODO: Generalize the y value
            annotation1.setFont(new Font("SansSerif", Font.PLAIN, 10));
            plot.addAnnotation(annotation1);
            final XYTextAnnotation annotation2 = new XYTextAnnotation("IFU-R", ifu2XRlocation, ifu2Ylocation);        // TODO: Generalize the y value
            annotation2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            plot.addAnnotation(annotation2);

            final Marker IFUB = new ValueMarker(200.0);
            IFUB.setPaint(Color.blue);
            IFUB.setLabel("IFU-B");
            IFUB.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);
            IFUB.setLabelTextAnchor(TextAnchor.HALF_ASCENT_LEFT);
            plot.addDomainMarker(IFUB);
            final Marker IFUR = new ValueMarker(150.0);
            IFUR.setPaint(Color.red);
            IFUR.setLabel("IFU-R");
            IFUR.setLabelAnchor(RectangleAnchor.BOTTOM_LEFT);
            IFUR.setLabelTextAnchor(TextAnchor.HALF_ASCENT_RIGHT);
            plot.addDomainMarker(IFUR);

        }

        // add all the data
        for (final SpcSeriesData d : JavaConversions.seqAsJavaList(s.series())) {
            addArray(d.data(), d.title(), d.color());
        }
    }

    public JFreeChart getChart() {
        return chart;
    }

    public BufferedImage getBufferedImage(final int width, final int height)  {
        return chart.createBufferedImage(width, height);
    }

    private void addArray(final double data[][], final String seriesName, final Option<Color> color) {
        final XYSeries newSeries = new XYSeries(seriesName);
        for (int i = 0; i < data[0].length; i++) {
            if (data[0][i] > 0)   ///!!!!keeps negative x values from being added to a chart!!!!
                newSeries.add(data[0][i], data[1][i]);
        }
        seriesData.addSeries(newSeries);

        final int ix = seriesData.getSeriesCount() - 1;
        final XYItemRenderer renderer = chart.getXYPlot().getRenderer();
        renderer.setSeriesPaint(ix, color.isDefined() ? color.get() : colorByIndex(ix));
        renderer.setSeriesStroke(ix, new BasicStroke(2));
    }

    private void setDomainMinMax(final double lower, final double upper) {
        chart.getXYPlot().getDomainAxis().setRange(lower, upper);
    }

}
