import { defineStore } from 'pinia';
import { getSystemConfig } from '@/api';

export const useConfigStore = defineStore('config', {
    state: () => ({
        sysName: '在线考试系统',
        logoUrl: 'https://gw.alipayobjects.com/zos/rmsportal/KDpgvguMpGfqaHPjicRK.svg',
        footerText: 'Copyright © 2026 在线考试系统',
        loading: false
    }),
    actions: {
        async fetchConfig() {
            this.loading = true;
            try {
                const res = await getSystemConfig();
                const data = res.data;
                if (data.sysName) this.sysName = data.sysName;
                if (data.logoUrl) this.logoUrl = data.logoUrl;
                if (data.footerText) this.footerText = data.footerText;
                document.title = this.sysName;
            } catch (e) {
                console.error('Failed to fetch system config', e);
            } finally {
                this.loading = false;
            }
        }
    }
});
