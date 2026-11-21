import type { GlobalThemeOverrides } from 'naive-ui';
import { defu } from 'defu';
import { addColorAlpha, getColorPalette, getPaletteColorByNumber, getRgb } from '@sa/color';
import { DARK_CLASS } from '@/constants/app';
import { toggleHtmlClass } from '@/utils/common';
import { localStg } from '@/utils/storage';
import { overrideThemeSettings, themeSettings } from '@/theme/settings';
import { themeVars } from '@/theme/vars';

/** Init theme settings */
export function initThemeSettings() {
  const isProd = import.meta.env.PROD;

  // if it is development mode, the theme settings will not be cached, by update `themeSettings` in `src/theme/settings.ts` to update theme settings
  if (!isProd) return themeSettings;

  // if it is production mode, the theme settings will be cached in localStorage
  // if want to update theme settings when publish new version, please update `overrideThemeSettings` in `src/theme/settings.ts`

  const localSettings = localStg.get('themeSettings');

  let settings = defu(localSettings, themeSettings);

  const isOverride = localStg.get('overrideThemeFlag') === BUILD_TIME;

  if (!isOverride) {
    settings = defu(overrideThemeSettings, settings);

    localStg.set('overrideThemeFlag', BUILD_TIME);
  }

  return settings;
}

/**
 * create theme token css vars value by theme settings
 *
 * @param colors Theme colors
 * @param tokens Theme setting tokens
 * @param [recommended=false] Use recommended color. Default is `false`
 */
export function createThemeToken(
  colors: App.Theme.ThemeColor,
  tokens?: App.Theme.ThemeSetting['tokens'],
  recommended = false
) {
  const paletteColors = createThemePaletteColors(colors, recommended);

  const { light, dark } = tokens || themeSettings.tokens;

  const themeTokens: App.Theme.ThemeTokenCSSVars = {
    colors: {
      ...paletteColors,
      nprogress: paletteColors.primary,
      ...light.colors
    },
    boxShadow: {
      ...light.boxShadow
    }
  };

  const darkThemeTokens: App.Theme.ThemeTokenCSSVars = {
    colors: {
      ...themeTokens.colors,
      ...dark?.colors
    },
    boxShadow: {
      ...themeTokens.boxShadow,
      ...dark?.boxShadow
    }
  };

  return {
    themeTokens,
    darkThemeTokens
  };
}

/**
 * Create theme palette colors
 *
 * @param colors Theme colors
 * @param [recommended=false] Use recommended color. Default is `false`
 */
function createThemePaletteColors(colors: App.Theme.ThemeColor, recommended = false) {
  const colorKeys = Object.keys(colors) as App.Theme.ThemeColorKey[];
  const colorPaletteVar = {} as App.Theme.ThemePaletteColor;

  colorKeys.forEach(key => {
    const colorMap = getColorPalette(colors[key], recommended);

    colorPaletteVar[key] = colorMap.get(500)!;

    colorMap.forEach((hex, number) => {
      colorPaletteVar[`${key}-${number}`] = hex;
    });
  });

  return colorPaletteVar;
}

/**
 * Get css var by tokens
 *
 * @param tokens Theme base tokens
 */
function getCssVarByTokens(tokens: App.Theme.BaseToken) {
  const styles: string[] = [];

  function removeVarPrefix(value: string) {
    return value.replace('var(', '').replace(')', '');
  }

  function removeRgbPrefix(value: string) {
    return value.replace('rgb(', '').replace(')', '');
  }

  for (const [key, tokenValues] of Object.entries(themeVars)) {
    for (const [tokenKey, tokenValue] of Object.entries(tokenValues)) {
      let cssVarsKey = removeVarPrefix(tokenValue);
      let cssValue = tokens[key][tokenKey];

      if (key === 'colors') {
        cssVarsKey = removeRgbPrefix(cssVarsKey);
        const { r, g, b } = getRgb(cssValue);
        cssValue = `${r} ${g} ${b}`;
      }

      styles.push(`${cssVarsKey}: ${cssValue}`);
    }
  }

  const styleStr = styles.join(';');

  return styleStr;
}

/**
 * Add theme vars to global
 *
 * @param tokens
 */
export function addThemeVarsToGlobal(tokens: App.Theme.BaseToken, darkTokens: App.Theme.BaseToken) {
  const cssVarStr = getCssVarByTokens(tokens);
  const darkCssVarStr = getCssVarByTokens(darkTokens);

  const css = `
    :root {
      ${cssVarStr}
    }
  `;

  const darkCss = `
    html.${DARK_CLASS} {
      ${darkCssVarStr}
    }
  `;

  const styleId = 'theme-vars';

  const style = document.querySelector(`#${styleId}`) || document.createElement('style');

  style.id = styleId;

  style.textContent = css + darkCss;

  document.head.appendChild(style);
}

/**
 * Toggle css dark mode
 *
 * @param darkMode Is dark mode
 */
export function toggleCssDarkMode(darkMode = false) {
  const { add, remove } = toggleHtmlClass(DARK_CLASS);

  if (darkMode) {
    add();
  } else {
    remove();
  }
}

/**
 * Toggle auxiliary color modes
 *
 * @param grayscaleMode
 * @param colourWeakness
 */
export function toggleAuxiliaryColorModes(grayscaleMode = false, colourWeakness = false) {
  const htmlElement = document.documentElement;
  htmlElement.style.filter = [grayscaleMode ? 'grayscale(100%)' : '', colourWeakness ? 'invert(80%)' : '']
    .filter(Boolean)
    .join(' ');
}

type NaiveColorScene = '' | 'Suppl' | 'Hover' | 'Pressed' | 'Active';
type NaiveColorKey = `${App.Theme.ThemeColorKey}Color${NaiveColorScene}`;
type NaiveThemeColor = Partial<Record<NaiveColorKey, string>>;
interface NaiveColorAction {
  scene: NaiveColorScene;
  handler: (color: string) => string;
}

/**
 * Get naive theme colors
 *
 * @param colors Theme colors
 * @param [recommended=false] Use recommended color. Default is `false`
 */
function getNaiveThemeColors(colors: App.Theme.ThemeColor, recommended = false) {
  const colorActions: NaiveColorAction[] = [
    { scene: '', handler: color => color },
    { scene: 'Suppl', handler: color => color },
    { scene: 'Hover', handler: color => getPaletteColorByNumber(color, 500, recommended) },
    { scene: 'Pressed', handler: color => getPaletteColorByNumber(color, 700, recommended) },
    { scene: 'Active', handler: color => addColorAlpha(color, 0.1) }
  ];

  const themeColors: NaiveThemeColor = {};

  const colorEntries = Object.entries(colors) as [App.Theme.ThemeColorKey, string][];

  colorEntries.forEach(color => {
    colorActions.forEach(action => {
      const [colorType, colorValue] = color;
      const colorKey: NaiveColorKey = `${colorType}Color${action.scene}`;
      themeColors[colorKey] = action.handler(colorValue);
    });
  });

  return themeColors;
}

/**
 * Get naive theme
 *
 * Editorial theme: paper canvas, ink type, hairlines instead of shadows, zero radius.
 *
 * @param colors Theme colors
 * @param [recommended=false] Use recommended color. Default is `false`
 * @param [isDark=false] Whether the dark palette is active. Default is `false`
 */
export function getNaiveTheme(colors: App.Theme.ThemeColor, recommended = false, isDark = false) {
  const { primary: colorLoading } = colors;

  const ink = isDark ? '#ece9e3' : '#1c1c1c';
  const paper = isDark ? '#1c1c1c' : '#f9f8f6';
  const graphite = isDark ? '#a1a1aa' : '#6b7280';
  const hairline = isDark ? 'rgba(236, 233, 227, 0.16)' : 'rgba(28, 28, 28, 0.12)';
  const hairlineStrong = isDark ? 'rgba(236, 233, 227, 0.4)' : 'rgba(28, 28, 28, 0.4)';
  const hoverWash = isDark ? 'rgba(236, 233, 227, 0.06)' : 'rgba(28, 28, 28, 0.04)';
  const sans =
    "Inter, system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif";
  const mono = "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace";

  const theme: GlobalThemeOverrides = {
    common: {
      ...getNaiveThemeColors(colors, recommended),
      fontFamily: sans,
      fontFamilyMono: mono,
      fontWeightStrong: '500',
      textColorBase: ink,
      textColor1: ink,
      textColor2: graphite,
      textColor3: isDark ? '#7c7c7c' : '#9ca3af',
      placeholderColor: isDark ? '#767676' : '#a8a29e',
      bodyColor: paper,
      cardColor: paper,
      modalColor: paper,
      popoverColor: paper,
      tableColor: paper,
      inputColor: paper,
      actionColor: hoverWash,
      hoverColor: hoverWash,
      borderColor: hairline,
      dividerColor: hairline,
      borderRadius: '0',
      borderRadiusSmall: '0',
      boxShadow1: 'none',
      boxShadow2: 'none',
      boxShadow3: 'none'
    },
    Layout: {
      color: paper,
      headerColor: paper,
      footerColor: paper,
      siderColor: paper,
      headerBorderColor: hairline,
      footerBorderColor: hairline,
      siderBorderColor: hairline
    },
    LoadingBar: {
      colorLoading
    },
    Button: {
      borderRadiusTiny: '0',
      borderRadiusSmall: '0',
      borderRadiusMedium: '0',
      borderRadiusLarge: '0',
      fontWeight: '500',
      fontSizeMedium: '13px',
      fontSizeLarge: '13px',
      color: 'transparent',
      colorHover: hoverWash,
      colorPressed: hoverWash,
      colorFocus: 'transparent',
      textColor: ink,
      textColorHover: ink,
      textColorPressed: ink,
      textColorFocus: ink,
      border: `1px solid ${hairline}`,
      borderHover: `1px solid ${hairlineStrong}`,
      borderPressed: `1px solid ${hairlineStrong}`,
      borderFocus: `1px solid ${ink}`,
      colorPrimary: ink,
      colorHoverPrimary: paper,
      colorPressedPrimary: paper,
      colorFocusPrimary: paper,
      textColorPrimary: paper,
      textColorHoverPrimary: ink,
      textColorPressedPrimary: ink,
      textColorFocusPrimary: ink,
      borderPrimary: `1px solid ${ink}`,
      borderHoverPrimary: `1px solid ${ink}`,
      borderPressedPrimary: `1px solid ${ink}`,
      borderFocusPrimary: `1px solid ${ink}`,
      rippleColor: isDark ? 'rgba(236, 233, 227, 0.16)' : 'rgba(28, 28, 28, 0.12)'
    },
    Card: {
      borderRadius: '0',
      color: paper,
      borderColor: hairline,
      textColor: ink,
      titleTextColor: ink,
      titleFontWeight: '500',
      titleFontSizeMedium: '20px',
      actionColor: 'transparent',
      boxShadow: 'none'
    },
    Input: {
      borderRadius: '0',
      color: paper,
      colorFocus: paper,
      textColor: ink,
      caretColor: ink,
      placeholderColor: isDark ? '#767676' : '#a8a29e',
      border: `1px solid ${hairline}`,
      borderHover: `1px solid ${hairlineStrong}`,
      borderFocus: `1px solid ${ink}`,
      borderError: `1px solid ${isDark ? '#a3746c' : '#8c5a52'}`,
      boxShadowFocus: 'none',
      heightMedium: '44px',
      heightLarge: '48px',
      iconColor: graphite
    },
    DataTable: {
      borderRadius: '0',
      borderColor: hairline,
      thColor: 'transparent',
      thColorHover: hoverWash,
      thTextColor: graphite,
      thFontWeight: '500',
      tdColor: paper,
      tdColorHover: hoverWash,
      tdTextColor: ink,
      boxShadowBefore: 'none',
      boxShadowAfter: 'none',
      loadingColor: ink
    },
    Menu: {
      itemHeight: '40px',
      fontSize: '13px',
      itemColorHover: hoverWash,
      itemColorActive: 'transparent',
      itemColorActiveHover: 'transparent',
      itemColorActiveCollapsed: 'transparent',
      itemTextColor: graphite,
      itemTextColorHover: ink,
      itemTextColorActive: ink,
      itemTextColorChildActive: ink,
      itemIconColor: graphite,
      itemIconColorHover: ink,
      itemIconColorActive: ink,
      itemIconColorChildActive: ink,
      arrowColor: graphite,
      arrowColorActive: ink,
      groupTextColor: graphite,
      dividerColor: hairline
    },
    Dialog: {
      borderRadius: '0',
      color: paper,
      textColor: ink,
      titleTextColor: ink,
      titleFontWeight: '500',
      border: `1px solid ${hairline}`
    },
    Modal: {
      color: paper,
      textColor: ink,
      boxShadow: 'none'
    },
    Popover: {
      borderRadius: '0',
      color: paper,
      textColor: ink,
      dividerColor: hairline,
      boxShadow: 'none'
    },
    Dropdown: {
      borderRadius: '0',
      color: paper,
      optionTextColor: ink,
      optionTextColorHover: ink,
      optionTextColorActive: ink,
      optionColorHover: hoverWash,
      optionColorActive: 'transparent',
      dividerColor: hairline
    },
    Tooltip: {
      borderRadius: '0',
      color: ink,
      textColor: paper,
      boxShadow: 'none'
    },
    Tag: {
      borderRadius: '0'
    },
    Alert: {
      borderRadius: '0'
    },
    Tabs: {
      tabBorderRadius: '0',
      barColor: ink,
      tabTextColorLine: graphite,
      tabTextColorHoverLine: ink,
      tabTextColorActiveLine: ink,
      tabFontWeightActive: '500'
    },
    Switch: {
      railBorderRadiusSmall: '0',
      railBorderRadiusMedium: '0',
      railBorderRadiusLarge: '0',
      buttonBorderRadiusSmall: '0',
      buttonBorderRadiusMedium: '0',
      buttonBorderRadiusLarge: '0',
      railColorActive: ink,
      boxShadowFocus: 'none'
    },
    Checkbox: {
      borderRadius: '0',
      colorChecked: ink,
      borderChecked: `1px solid ${ink}`,
      boxShadowFocus: 'none'
    },
    Radio: {
      buttonBorderRadius: '0',
      buttonColorActive: ink,
      buttonTextColorActive: paper,
      buttonBorderColorActive: ink,
      boxShadowFocus: 'none',
      boxShadowActive: 'none'
    },
    Pagination: {
      itemBorderRadius: '0',
      itemColorActive: 'transparent',
      itemTextColorActive: ink,
      itemBorderActive: `1px solid ${ink}`
    },
    Drawer: {
      borderRadius: '0',
      color: paper,
      textColor: ink,
      titleTextColor: ink,
      boxShadow: 'none'
    },
    Scrollbar: {
      borderRadius: '0',
      color: isDark ? 'rgba(236, 233, 227, 0.2)' : 'rgba(28, 28, 28, 0.2)',
      colorHover: isDark ? 'rgba(236, 233, 227, 0.32)' : 'rgba(28, 28, 28, 0.32)'
    }
  };

  return theme;
}
