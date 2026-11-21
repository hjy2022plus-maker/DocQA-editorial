import { computed, effectScope, nextTick, onScopeDispose, ref, watch } from 'vue';
import { useElementSize } from '@vueuse/core';
import * as echarts from 'echarts/core';
import { BarChart, GaugeChart, LineChart, PictorialBarChart, PieChart, RadarChart, ScatterChart } from 'echarts/charts';
import type {
  BarSeriesOption,
  GaugeSeriesOption,
  LineSeriesOption,
  PictorialBarSeriesOption,
  PieSeriesOption,
  RadarSeriesOption,
  ScatterSeriesOption
} from 'echarts/charts';
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  TransformComponent
} from 'echarts/components';
import type {
  DatasetComponentOption,
  GridComponentOption,
  LegendComponentOption,
  TitleComponentOption,
  ToolboxComponentOption,
  TooltipComponentOption
} from 'echarts/components';
import { LabelLayout, UniversalTransition } from 'echarts/features';
import { CanvasRenderer } from 'echarts/renderers';
import { useThemeStore } from '@/store/modules/theme';

export type ECOption = echarts.ComposeOption<
  | BarSeriesOption
  | LineSeriesOption
  | PieSeriesOption
  | ScatterSeriesOption
  | PictorialBarSeriesOption
  | RadarSeriesOption
  | GaugeSeriesOption
  | TitleComponentOption
  | LegendComponentOption
  | TooltipComponentOption
  | GridComponentOption
  | ToolboxComponentOption
  | DatasetComponentOption
>;

echarts.use([
  TitleComponent,
  LegendComponent,
  TooltipComponent,
  GridComponent,
  DatasetComponent,
  TransformComponent,
  ToolboxComponent,
  BarChart,
  LineChart,
  PieChart,
  ScatterChart,
  PictorialBarChart,
  RadarChart,
  GaugeChart,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer
]);

/** Editorial chart palette: ink, graphite and a single mauve accent. */
echarts.registerTheme('editorial', {
  color: ['#1c1c1c', '#816d70', '#6b7280', '#a8a29e', '#4b5563', '#b9b2a8'],
  backgroundColor: 'transparent',
  textStyle: { color: '#1c1c1c' },
  title: { textStyle: { color: '#1c1c1c', fontWeight: 400 } },
  legend: { textStyle: { color: '#6b7280' } },
  tooltip: {
    backgroundColor: '#f9f8f6',
    borderColor: 'rgba(28, 28, 28, 0.1)',
    borderWidth: 1,
    textStyle: { color: '#1c1c1c' }
  },
  categoryAxis: {
    axisLine: { lineStyle: { color: 'rgba(28, 28, 28, 0.2)' } },
    axisTick: { lineStyle: { color: 'rgba(28, 28, 28, 0.2)' } },
    axisLabel: { color: '#6b7280' },
    splitLine: { show: false }
  },
  valueAxis: {
    axisLine: { show: false },
    axisLabel: { color: '#6b7280' },
    splitLine: { lineStyle: { color: 'rgba(28, 28, 28, 0.08)' } }
  }
});

interface ChartHooks {
  onRender?: (chart: echarts.ECharts) => void | Promise<void>;
  onUpdated?: (chart: echarts.ECharts) => void | Promise<void>;
  onDestroy?: (chart: echarts.ECharts) => void | Promise<void>;
}

/**
 * use echarts
 *
 * @param optionsFactory echarts options factory function
 * @param darkMode dark mode
 */
export function useEcharts<T extends ECOption>(optionsFactory: () => T, hooks: ChartHooks = {}) {
  const scope = effectScope();

  const themeStore = useThemeStore();
  const darkMode = computed(() => themeStore.darkMode);

  const domRef = ref<HTMLElement | null>(null);
  const initialSize = { width: 0, height: 0 };
  const { width, height } = useElementSize(domRef, initialSize);

  let chart: echarts.ECharts | null = null;
  const chartOptions: T = optionsFactory();

  const {
    onRender = instance => {
      const textColor = darkMode.value ? 'rgb(224, 224, 224)' : 'rgb(31, 31, 31)';
      const maskColor = darkMode.value ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.8)';

      instance.showLoading({
        color: themeStore.themeColor,
        textColor,
        fontSize: 14,
        maskColor
      });
    },
    onUpdated = instance => {
      instance.hideLoading();
    },
    onDestroy
  } = hooks;

  /**
   * whether can render chart
   *
   * when domRef is ready and initialSize is valid
   */
  function canRender() {
    return domRef.value && initialSize.width > 0 && initialSize.height > 0;
  }

  /** is chart rendered */
  function isRendered() {
    return Boolean(domRef.value && chart);
  }

  /**
   * update chart options
   *
   * @param callback callback function
   */
  async function updateOptions(callback: (opts: T, optsFactory: () => T) => ECOption = () => chartOptions) {
    if (!isRendered()) return;

    const updatedOpts = callback(chartOptions, optionsFactory);

    Object.assign(chartOptions, updatedOpts);

    if (isRendered()) {
      chart?.clear();
    }

    chart?.setOption({ ...updatedOpts, backgroundColor: 'transparent' });

    await onUpdated?.(chart!);
  }

  function setOptions(options: T) {
    chart?.setOption(options);
  }

  /** render chart */
  async function render() {
    if (!isRendered()) {
      const chartTheme = darkMode.value ? 'dark' : 'editorial';

      await nextTick();

      chart = echarts.init(domRef.value, chartTheme);

      chart.setOption({ ...chartOptions, backgroundColor: 'transparent' });

      await onRender?.(chart);
    }
  }

  /** resize chart */
  function resize() {
    chart?.resize();
  }

  /** destroy chart */
  async function destroy() {
    if (!chart) return;

    await onDestroy?.(chart);
    chart?.dispose();
    chart = null;
  }

  /** change chart theme */
  async function changeTheme() {
    await destroy();
    await render();
    await onUpdated?.(chart!);
  }

  /**
   * render chart by size
   *
   * @param w width
   * @param h height
   */
  async function renderChartBySize(w: number, h: number) {
    initialSize.width = w;
    initialSize.height = h;

    // size is abnormal, destroy chart
    if (!canRender()) {
      await destroy();

      return;
    }

    // resize chart
    if (isRendered()) {
      resize();
    }

    // render chart
    await render();

    if (chart) {
      await onUpdated?.(chart);
    }
  }

  scope.run(() => {
    watch([width, height], ([newWidth, newHeight]) => {
      renderChartBySize(newWidth, newHeight);
    });

    watch(darkMode, () => {
      changeTheme();
    });
  });

  onScopeDispose(() => {
    destroy();
    scope.stop();
  });

  return {
    domRef,
    updateOptions,
    setOptions
  };
}
