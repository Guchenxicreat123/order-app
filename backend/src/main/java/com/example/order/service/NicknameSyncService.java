package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.order.entity.FamilyMember;
import com.example.order.entity.MenuItem;
import com.example.order.entity.Order;
import com.example.order.entity.User;
import com.example.order.mapper.FamilyMemberMapper;
import com.example.order.mapper.MenuItemMapper;
import com.example.order.mapper.OrderMapper;
import com.example.order.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户昵称同步服务。
 *
 * 昵称展示的几处为了免 join 做了「快照冗余」：
 *   1. t_family_member.nickname    —— 家庭成员列表/主厨名/创建者名
 *   2. t_order.user_nickname       —— 订单「下单者」显示
 *   3. t_menu_item.user_nickname   —— 点菜单项「点单人」显示
 *
 * 用户改名后调用 syncAll()，把上述全部快照刷新为最新昵称，
 * 保证「我的」页改一次名，全站看到的名字都与 t_user.nickname 一致。
 */
@Service
public class NicknameSyncService {

    @Autowired private UserMapper userMapper;
    @Autowired private FamilyMemberMapper familyMemberMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private MenuItemMapper menuItemMapper;

    /**
     * 修改用户昵称并同步所有冗余快照（事务内原子完成）。
     */
    @Transactional
    public void changeNicknameAndSync(String openId, String newNickname) {
        // 1. 更新主表
        User u = userMapper.selectById(openId);
        if (u == null) throw new RuntimeException("用户不存在");
        u.setNickname(newNickname);
        u.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(u);

        // 2. 家庭成员快照
        familyMemberMapper.update(null,
                new LambdaUpdateWrapper<FamilyMember>()
                        .eq(FamilyMember::getUserId, openId)
                        .set(FamilyMember::getNickname, newNickname));

        // 3. 订单「下单者」快照
        orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getUserId, openId)
                        .set(Order::getUserNickname, newNickname));

        // 4. 点菜单项「点单人」快照
        menuItemMapper.update(null,
                new LambdaUpdateWrapper<MenuItem>()
                        .eq(MenuItem::getUserId, openId)
                        .set(MenuItem::getUserNickname, newNickname));
    }
}
