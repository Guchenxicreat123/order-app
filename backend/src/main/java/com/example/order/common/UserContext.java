package com.example.order.common;

/** 上下文持有当前请求的用户/主厨身份和家庭 */
public class UserContext {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CHEF = new ThreadLocal<>();
    private static final ThreadLocal<Long> FAMILY = new ThreadLocal<>();

    public static void set(String openId) { USER.set(openId); }
    public static String get() { return USER.get(); }

    public static void setChef(String openId) { USER.set(openId); CHEF.set(true); }
    public static boolean isChef() { return Boolean.TRUE.equals(CHEF.get()); }
    public static String getChef() { return USER.get(); }

    public static void setFamily(Long familyId) { FAMILY.set(familyId); }
    public static Long getFamily() { return FAMILY.get(); }

    public static void clear() {
        USER.remove();
        CHEF.remove();
        FAMILY.remove();
    }
}