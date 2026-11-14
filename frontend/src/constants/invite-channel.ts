export const inviteChannelConfig = {
  officialAccountName: '管理员',
  replyKeywords: ['邀请码'],
  qrCodeImageUrl: ''
} as const;

export function buildInviteChannelGuide() {
  return '请向管理员获取注册邀请码。';
}

export function buildInviteCodeShareMessage(shareLink: string, inviteCode: string) {
  return [
    '文档问答系统邀请码：',
    `邀请码：${inviteCode}`,
    `注册链接：${shareLink}`
  ].join('\n');
}
