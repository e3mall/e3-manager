package cn.e3mall.service;

import cn.e3mall.common.pojo.EasyUIDataGridResult;
import cn.e3mall.common.utils.E3Result;
import cn.e3mall.pojo.TbItem;
import cn.e3mall.pojo.TbItemDesc;

public interface ItemService {
	public TbItem getItemById(long id);
	public EasyUIDataGridResult getItemList(int page, int rows);
	public E3Result addItem(TbItem item, String desc);
	public E3Result getItemDescById(Long itemId);
	public E3Result updateItem(TbItem item, String desc);
}
