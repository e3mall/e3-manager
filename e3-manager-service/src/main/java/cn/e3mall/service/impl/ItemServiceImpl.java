package cn.e3mall.service.impl;

import java.util.Date;
import java.util.List;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import cn.e3mall.common.jedis.JedisClient;
import cn.e3mall.common.pojo.EasyUIDataGridResult;
import cn.e3mall.common.utils.E3Result;
import cn.e3mall.common.utils.IDUtils;
import cn.e3mall.common.utils.JsonUtils;
import cn.e3mall.mapper.TbItemDescMapper;
import cn.e3mall.mapper.TbItemMapper;
import cn.e3mall.pojo.TbContent;
import cn.e3mall.pojo.TbItem;
import cn.e3mall.pojo.TbItemDesc;
import cn.e3mall.pojo.TbItemExample;
import cn.e3mall.service.ItemService;

/**
 * 商品管理service
 * 
 * @author Frank
 *
 */
@Service
public class ItemServiceImpl implements ItemService {
	@Value("${REDIS_ITEM_PRE}")
	private String REDIS_ITEM_PRE;
	@Value("${ITEM_CACHE_EXPIRE}")
	private Integer ITEM_CACHE_EXPIRE;
	@Autowired
	private TbItemMapper itemMapper;
	@Autowired
	private TbItemDescMapper itemDescMapper;
	@Autowired
	private JmsTemplate jmsTemplate;
	@Autowired
	private Destination destination;
	@Autowired
	private JedisClient jedisClient;
	
	@Override
	public TbItem getItemById(long id) {
		//查询缓存
		try {
			String json = jedisClient.get(REDIS_ITEM_PRE+":"+id+":BASE");
			//如果缓存中存在则返回缓存中的内容
			if(StringUtils.isNotBlank(json)){
				TbItem item = JsonUtils.jsonToPojo(json, TbItem.class);
				return item;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		//不存在则查询数据库
		TbItem item = itemMapper.selectByPrimaryKey(id);
		//写入缓存
		try {
			jedisClient.set(REDIS_ITEM_PRE+":"+item.getId()+":BASE", JsonUtils.objectToJson(item));
			//设置过期时间
			jedisClient.expire(REDIS_ITEM_PRE+":"+item.getId()+":BASE", ITEM_CACHE_EXPIRE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return item;
	}

	@Override
	public EasyUIDataGridResult getItemList(int page, int rows) {
		// 设置分页信息
		PageHelper.startPage(page, rows);
		// 执行查询
		TbItemExample example = new TbItemExample();
		List<TbItem> list = itemMapper.selectByExample(example);// 执行查询时就已经融入了分页信息
		// 取分页信息
		PageInfo<TbItem> pageInfo = new PageInfo<>(list);

		// 创建返回结果对象
		EasyUIDataGridResult result = new EasyUIDataGridResult();
		result.setTotal(pageInfo.getTotal());
		result.setRows(list);

		return result;

	}

	@Override
	public E3Result addItem(TbItem item, String desc) {
		// 添加商品ID
		final long itemId = IDUtils.genItemId();
		item.setId(itemId);
		// 补全item中的属性
		item.setStatus((byte) 1);// 1 正常 2下架 3 删除
		item.setCreated(new Date());
		item.setUpdated(new Date());
		// 向tb_item表中插入
		itemMapper.insert(item);
		//插入数据库后要做缓存同步
		/*try {
			jedisClient.hdel(CONTENT_LIST, content.getCategoryId()+"");
		} catch (Exception e) {
			e.printStackTrace();
		}*/
		// 创建一个商品描述表对应的对象
		TbItemDesc itemDesc = new TbItemDesc();
		// 补全该对象的id和信息
		itemDesc.setItemId(itemId);
		itemDesc.setItemDesc(desc);
		itemDesc.setCreated(new Date());
		itemDesc.setUpdated(new Date());
		// 向tb_item_desc中插入该对象
		itemDescMapper.insert(itemDesc);
		//在消息发送之前等待一会，以防接收者查询数据库找不到商品
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//向activeMq发送消息更新索引库
		jmsTemplate.send(destination, new MessageCreator() {

			@Override
			public Message createMessage(Session session) throws JMSException {
				// 创建一个消息对象并返回
				TextMessage textMessage = session.createTextMessage(itemId+"");
				return textMessage;
			}
		});
		// 返回成功
		return E3Result.ok();
	}

	@Override
	public E3Result getItemDescById(Long itemId) {
		//查询缓存
		try {
			String json = jedisClient.get(REDIS_ITEM_PRE+":"+itemId+":DESC");
			//如果缓存中存在则返回缓存中的内容
			if(StringUtils.isNotBlank(json)){
				TbItemDesc itemDesc = JsonUtils.jsonToPojo(json, TbItemDesc.class);
				return new E3Result(itemDesc);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		//不存在则查询数据库
		TbItemDesc itemDesc = itemDescMapper.selectByPrimaryKey(itemId);
		//写入缓存
		try {
			jedisClient.set(REDIS_ITEM_PRE+":"+itemDesc.getItemId()+":DESC", JsonUtils.objectToJson(itemDesc));
			//设置过期时间
			jedisClient.expire(REDIS_ITEM_PRE+":"+itemDesc.getItemId()+":DESC", ITEM_CACHE_EXPIRE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		E3Result result = new E3Result(itemDesc);
		return result;
	}

	@Override
	public E3Result updateItem(TbItem item, String desc) {
		item.setUpdated(new Date());
		// 向tb_item表中更新
		itemMapper.updateByPrimaryKeySelective(item);
		// 创建一个商品描述表对应的对象
		TbItemDesc itemDesc = new TbItemDesc();
		// 补全该对象的id和信息
		itemDesc.setItemId(item.getId());
		itemDesc.setItemDesc(desc);
		//itemDesc.setCreated(new Date());
		itemDesc.setUpdated(new Date());
		// 向tb_item_desc中更新该对象
		itemDescMapper.updateByPrimaryKeySelective(itemDesc);
		// 返回成功
		return E3Result.ok();
	}

}
