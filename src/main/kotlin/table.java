import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

import java.io.FileOutputStream;
import java.io.IOException;

public class table {
    public void test(){
        // 创建数据集
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Chrome", 60);
        dataset.setValue("Firefox", 25);
        dataset.setValue("Safari", 10);
        dataset.setValue("Others", 5);

        // 创建JFreeChart对象
        JFreeChart chart = ChartFactory.createPieChart(
                "Browser market share",  // 图表标题
                dataset,  // 数据集
                true,     // 是否显示图例
                true,     // 是否生成工具提示
                false    // 是否生成URL链接
        );

        // 输出为PNG图片
        FileOutputStream outputFile = null;
        try {
            outputFile = new FileOutputStream("piechart.png");
            ChartUtils.writeChartAsPNG(outputFile, chart, 500, 500);
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }
}
