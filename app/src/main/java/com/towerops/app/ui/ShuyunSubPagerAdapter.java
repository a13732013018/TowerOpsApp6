package com.towerops.app.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 数运工单子Tab适配器 - 用于"数运监控"和"数运审核"Tab切换
 */
public class ShuyunSubPagerAdapter extends FragmentStateAdapter {

    private static final int TAB_COUNT = 2;
    private final List<Fragment> fragments = new ArrayList<>();

    public ShuyunSubPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new ShuyunMonitorFragment();
                break;
            case 1:
                fragment = new ShuyunAuditFragment();
                break;
            default:
                fragment = new ShuyunMonitorFragment();
        }
        // 保存 fragment 引用
        while (fragments.size() <= position) {
            fragments.add(null);
        }
        fragments.set(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }

    /**
     * 根据位置获取 Fragment
     */
    public Fragment getFragment(int position) {
        if (position >= 0 && position < fragments.size()) {
            return fragments.get(position);
        }
        return null;
    }

    /**
     * 获取 ShuyunMonitorFragment
     */
    public ShuyunMonitorFragment getMonitorFragment() {
        Fragment fragment = getFragment(0);
        if (fragment instanceof ShuyunMonitorFragment) {
            return (ShuyunMonitorFragment) fragment;
        }
        return null;
    }

    /**
     * 获取 ShuyunAuditFragment
     */
    public ShuyunAuditFragment getAuditFragment() {
        Fragment fragment = getFragment(1);
        if (fragment instanceof ShuyunAuditFragment) {
            return (ShuyunAuditFragment) fragment;
        }
        return null;
    }
}
