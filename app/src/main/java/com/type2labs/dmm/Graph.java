package com.type2labs.dmm;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Random;

/**
 * Created by Mudkipz on 17/03/2017.
 */

public class Graph {
    private static final Random RANDOM = new Random();
    private GraphView graph;
    private LineGraphSeries<DataPoint> series;
    private int lastX = 0;


    Graph(GraphView graph, LineGraphSeries<DataPoint> series) {
        this.graph = graph;
        this.series = series;
        initGraph();
    }

    void addData(String value) {
//        series.appendData(new DataPoint(lastX++, Double.parseDouble(ValueUtils.getValue(value))), true, 10);
    }

    void addRandom() {
        series.appendData(new DataPoint(lastX++, RANDOM.nextDouble() * 10d), true, 10);
    }

    private void initGraph() {
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(4);
    }

    private void removeOldValues() {

    }
}
