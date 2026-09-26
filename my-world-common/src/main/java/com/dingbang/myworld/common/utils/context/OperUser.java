package com.dingbang.myworld.common.utils.context;

import lombok.Data;

/**
 * 当前操作用户信息。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Data
public class OperUser {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String name;

    /**
     * 员工工号
     */
    private String empNo;

    /**
     * 员工姓名
     */
    private String empName;

    /**
     * 是否系统用户
     */
    private Boolean system;

    /**
     * 系统内置用户
     *
     * @return user
     */
    public static OperUser system() {
        OperUser user = new OperUser();
        user.setId(0L);
        user.setName("system");
        user.setEmpNo("0");
        user.setEmpName("system");
        user.setSystem(Boolean.TRUE);
        return user;
    }
}
