package cn.e3mall.service;

import java.util.List;

import cn.e3mall.common.pojo.EasyUITreeNode;

public interface ItemCatService {
	//根据父节点id查找子节点列表
	public List<EasyUITreeNode> getItemCatList(long parentId);
	
}
