/** Editorial light/dark surface tokens */
const lightTokens: App.Theme.ThemeSettingToken = {
  colors: {
    container: '#f9f8f6',
    layout: '#f9f8f6',
    inverted: '#1c1c1c',
    'base-text': '#1c1c1c'
  },
  boxShadow: {
    header: 'none',
    sider: 'none',
    tab: 'none'
  }
};

const darkTokens: App.Theme.ThemeSettingToken = {
  colors: { container: '#1c1c1c', layout: '#171716', inverted: '#f9f8f6', 'base-text': '#ece9e3' },
  boxShadow: {
    header: 'none',
    sider: 'none',
    tab: 'none'
  }
};

/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  themeScheme: 'light',
  grayscale: false,
  colourWeakness: false,
  recommendColor: true,
  themeColor: '#1c1c1c',
  otherColor: { info: '#1c1c1c', success: '#4f6350', warning: '#8a7355', error: '#8c5a52' },
  isInfoFollowPrimary: true,
  resetCacheStrategy: 'close',
  layout: { mode: 'vertical', scrollMode: 'content', reverseHorizontalMix: false },
  page: { animate: true, animateMode: 'fade-slide' },
  header: { height: 64, breadcrumb: { visible: false, showIcon: true }, multilingual: { visible: false } },
  tab: { visible: false, cache: true, height: 44, mode: 'chrome' },
  fixedHeaderAndTab: true,
  sider: {
    inverted: false,
    width: 208,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  footer: { visible: false, fixed: false, height: 48, right: true },
  watermark: { visible: false, text: '文档问答' },
  tokens: {
    light: lightTokens,
    dark: darkTokens
  }
};

/**
 * Override theme settings
 *
 * If publish new version, use `overrideThemeSettings` to override certain theme settings
 *
 * Production builds cache theme settings in localStorage, so returning visitors would keep the previous palette. These
 * overrides migrate existing caches to the editorial palette once per build.
 */
export const overrideThemeSettings: Partial<App.Theme.ThemeSetting> = {
  themeScheme: 'light',
  themeColor: '#1c1c1c',
  otherColor: { info: '#1c1c1c', success: '#4f6350', warning: '#8a7355', error: '#8c5a52' },
  header: { height: 64, breadcrumb: { visible: false, showIcon: true }, multilingual: { visible: false } },
  sider: {
    inverted: false,
    width: 208,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  tokens: {
    light: lightTokens,
    dark: darkTokens
  }
};
