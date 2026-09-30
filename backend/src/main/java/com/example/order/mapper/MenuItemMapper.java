package com.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.entity.MenuItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MenuItemMapper extends BaseMapper<MenuItem> {

    /** 乐观锁确认（version 匹配才更新，重复点击只生效一次） */
    @Update("UPDATE t_menu_item SET status=#{status}, confirmed_by=#{confirmedBy}, " +
            "confirmed_at=NOW(), version=version+1 " +
            "WHERE id=#{id} AND version=#{version} AND status=0")
    int confirmWithLock(@Param("id") Long id,
                         @Param("status") Integer status,
                         @Param("confirmedBy") String confirmedBy,
                         @Param("version") Integer version);

    /** 乐观锁撤销（主厨可撤已确认或已下单，下单人只撤自己的已下单；service 层已做权限校验） */
    @Update("UPDATE t_menu_item SET status=-1, version=version+1 " +
            "WHERE id=#{id} AND version=#{version} AND status IN (0,1)")
    int cancelWithLock(@Param("id") Long id, @Param("version") Integer version);
}
