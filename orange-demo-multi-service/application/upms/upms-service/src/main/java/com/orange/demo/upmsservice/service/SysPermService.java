package com.orange.demo.upmsservice.service;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.orange.demo.common.core.base.service.BaseService;
import com.orange.demo.common.sequence.wrapper.IdGeneratorWrapper;
import com.orange.demo.common.core.base.dao.BaseDaoMapper;
import com.orange.demo.common.core.object.MyRelationParam;
import com.orange.demo.common.core.constant.GlobalDeletedFlag;
import com.orange.demo.common.core.object.CallResult;
import com.orange.demo.upmsinterface.dto.SysPermDto;
import com.orange.demo.upmsservice.dao.SysPermCodePermMapper;
import com.orange.demo.upmsservice.dao.SysPermMapper;
import com.orange.demo.upmsservice.model.SysPerm;
import com.orange.demo.upmsservice.model.SysPermCodePerm;
import com.orange.demo.upmsservice.model.SysPermModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 权限资源数据服务类。
 *
 * @author Jerry
 * @date 2020-08-08
 */
@Service
public class SysPermService extends BaseService<SysPerm, SysPermDto, Long> {

    @Autowired
    private SysPermMapper sysPermMapper;
    @Autowired
    private SysPermCodePermMapper sysPermCodePermMapper;
    @Autowired
    private SysPermModuleService sysPermModuleService;
    @Autowired
    private IdGeneratorWrapper idGenerator;

    /**
     * 返回主对象的Mapper对象。
     *
     * @return 主对象的Mapper对象。
     */
    @Override
    protected BaseDaoMapper<SysPerm> mapper() {
        return sysPermMapper;
    }

    /**
     * 保存新增的权限资源对象。
     *
     * @param perm 新增的权限资源对象。
     * @return 新增后的权限资源对象。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysPerm saveNew(SysPerm perm) {
        perm.setPermId(idGenerator.nextLongId());
        perm.setCreateTime(new Date());
        perm.setDeletedFlag(GlobalDeletedFlag.NORMAL);
        sysPermMapper.insert(perm);
        return perm;
    }

    /**
     * 更新权限资源对象。
     *
     * @param perm         更新的权限资源对象。
     * @param originalPerm 原有的权限资源对象。
     * @return 更新成功返回true，否则false。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SysPerm perm, SysPerm originalPerm) {
        perm.setCreateTime(originalPerm.getCreateTime());
        perm.setDeletedFlag(GlobalDeletedFlag.NORMAL);
        return sysPermMapper.updateByPrimaryKeySelective(perm) != 0;
    }

    /**
     * 删除权限资源。
     *
     * @param permId 权限资源主键Id。
     * @return 删除成功返回true，否则false。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long permId) {
        SysPerm perm = new SysPerm();
        perm.setPermId(permId);
        perm.setDeletedFlag(GlobalDeletedFlag.DELETED);
        if (sysPermMapper.updateByPrimaryKeySelective(perm) != 1) {
            return false;
        }
        SysPermCodePerm permCodePerm = new SysPermCodePerm();
        permCodePerm.setPermId(permId);
        sysPermCodePermMapper.delete(permCodePerm);
        return true;
    }

    /**
     * 获取权限数据列表。
     *
     * @param sysPermFilter 过滤对象。
     * @return 权限列表。
     */
    public List<SysPerm> getPermListWithRelation(SysPerm sysPermFilter) {
        Example e = new Example(SysPerm.class);
        e.orderBy("showOrder");
        Example.Criteria c = e.createCriteria();
        if (ObjectUtil.isNotNull(sysPermFilter.getModuleId())) {
            c.andEqualTo("moduleId", sysPermFilter.getModuleId());
        }
        if (ObjectUtil.isNotNull(sysPermFilter.getUrl())) {
            c.andLike("url", "%" + sysPermFilter.getUrl() + "%");
        }
        c.andEqualTo("deletedFlag", GlobalDeletedFlag.NORMAL);
        List<SysPerm> permList = sysPermMapper.selectByExample(e);
        // 这里因为权限只有字典数据，所以仅仅做字典关联。
        this.buildRelationForDataList(permList, MyRelationParam.dictOnly(), null);
        return permList;
    }

    /**
     * 获取与指定权限字关联的权限资源列表。
     *
     * @param permCodeId 关联的权限字主键Id。
     * @param orderBy    排序参数。
     * @return 与指定权限字Id关联的权限资源列表。
     */
    public List<SysPerm> getPermListByPermCodeId(Long permCodeId, String orderBy) {
        return sysPermMapper.getPermListByPermCodeId(permCodeId, orderBy);
    }

    /**
     * 获取与指定用户关联的权限资源列表。
     *
     * @param userId 关联的用户主键Id。
     * @return 与指定用户Id关联的权限资源列表。
     */
    public List<SysPerm> getPermListByUserId(Long userId) {
        return sysPermMapper.getPermListByUserId(userId);
    }

    /**
     * 验证权限资源对象关联的数据是否都合法。
     *
     * @param sysPerm         当前操作的对象。
     * @param originalSysPerm 原有对象。
     * @return 验证结果。
     */
    public CallResult verifyRelatedData(SysPerm sysPerm, SysPerm originalSysPerm) {
        JSONObject jsonObject = null;
        if (this.needToVerify(sysPerm, originalSysPerm, SysPerm::getModuleId)) {
            SysPermModule permModule = sysPermModuleService.getById(sysPerm.getModuleId());
            if (permModule == null) {
                return CallResult.error("数据验证失败，关联的权限模块Id并不存在，请刷新后重试！");
            }
            jsonObject = new JSONObject();
            jsonObject.put("permModule", permModule);
        }
        return CallResult.ok(jsonObject);
    }
    
    /**
     * 查询权限资源地址的用户列表。同时返回详细的分配路径。
     *
     * @param permId    权限资源Id。
     * @param loginName 登录名。
     * @return 包含从权限资源到用户的完整权限分配路径信息的查询结果列表。
     */
    public List<Map<String, Object>> getSysUserListWithDetail(Long permId, String loginName) {
        return sysPermMapper.getSysUserListWithDetail(permId, loginName);
    }

    /**
     * 查询权限资源地址的角色列表。同时返回详细的分配路径。
     *
     * @param permId   权限资源Id。
     * @param roleName 角色名。
     * @return 包含从权限资源到角色的权限分配路径信息的查询结果列表。
     */
    public List<Map<String, Object>> getSysRoleListWithDetail(Long permId, String roleName) {
        return sysPermMapper.getSysRoleListWithDetail(permId, roleName);
    }

    /**
     * 查询权限资源地址的菜单列表。同时返回详细的分配路径。
     *
     * @param permId   权限资源Id。
     * @param menuName 菜单名。
     * @return 包含从权限资源到菜单的权限分配路径信息的查询结果列表。
     */
    public List<Map<String, Object>> getSysMenuListWithDetail(Long permId, String menuName) {
        return sysPermMapper.getSysMenuListWithDetail(permId, menuName);
    }
}
