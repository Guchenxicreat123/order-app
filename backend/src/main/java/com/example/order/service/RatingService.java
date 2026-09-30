package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.common.Result;
import com.example.order.common.UserContext;
import com.example.order.entity.MenuItem;
import com.example.order.entity.MenuItemRating;
import com.example.order.mapper.MenuItemMapper;
import com.example.order.mapper.MenuItemRatingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RatingService {

    @Autowired private MenuItemRatingMapper ratingMapper;
    @Autowired private MenuItemMapper menuItemMapper;

    /** 给菜单项评分（1-5星） */
    public Result<Void> rate(Long menuItemId, Integer score, String comment) {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        if (score == null || score < 1 || score > 5) return Result.error(400, "评分必须是1-5星");

        MenuItem item = menuItemMapper.selectById(menuItemId);
        if (item == null) return Result.error(404, "菜单项不存在");

        // 查重
        MenuItemRating existing = ratingMapper.selectOne(
                new LambdaQueryWrapper<MenuItemRating>()
                        .eq(MenuItemRating::getMenuItemId, menuItemId)
                        .eq(MenuItemRating::getUserId, userId));
        if (existing != null) {
            existing.setScore(score);
            existing.setComment(comment);
            ratingMapper.updateById(existing);
        } else {
            MenuItemRating r = new MenuItemRating();
            r.setMenuItemId(menuItemId);
            r.setUserId(userId);
            r.setScore(score);
            r.setComment(comment);
            r.setCreatedAt(LocalDateTime.now());
            ratingMapper.insert(r);
        }
        return Result.ok();
    }

    /** 某菜单项的平均分 */
    public Result<Double> avgScore(Long menuItemId) {
        var list = ratingMapper.selectList(
                new LambdaQueryWrapper<MenuItemRating>()
                        .eq(MenuItemRating::getMenuItemId, menuItemId));
        if (list.isEmpty()) return Result.ok(0.0);
        double avg = list.stream().mapToInt(MenuItemRating::getScore).average().orElse(0);
        return Result.ok(Math.round(avg * 10) / 10.0);
    }

    /** 我的评分记录 */
    public Result<?> myRatings() {
        String userId = UserContext.get();
        if (userId == null) return Result.error(401, "请先登录");
        return Result.ok(ratingMapper.selectList(
                new LambdaQueryWrapper<MenuItemRating>()
                        .eq(MenuItemRating::getUserId, userId)
                        .orderByDesc(MenuItemRating::getCreatedAt)));
    }
}
