-- Table structure for the BitSelect business database ragent_bit
--
-- Owned by mcp-server and created at startup. The initializer only loads data and does not own the DDL script
--
-- This database must not hold data that has to survive a schema version. Once the schema version lags, dropdb and start over.
-- This project does not do incremental migrations. When this file changes, bump BitSchemaInitializer.SCHEMA_VERSION with it
--
-- Table conventions:
--   Every table has an id BIGINT surrogate key. Business codes use a UNIQUE constraint. Using a business code as the primary key, when the coding rule changes,
--   would touch every row that references it. The surrogate key closes that path.
--   Every table has create_time and update_time. The application fills them via BitMetaObjectHandler. The database sets no DEFAULT.
--   Bulk seed loading does not go through MyBatis, so that SQL must set the timestamps itself.
--   Time-meaning columns use their own names (receive_time for receipt, use_time for redemption).
--   Legal status values are enforced by executors. The database sets no CHECK.
--   Keep only business unique constraints. Add performance indexes when a query is actually slow.
--   Amounts are always NUMERIC(10,2). Coupon thresholds need exact equality, and floats mis-judge at 299.999…
--   Times are always TIMESTAMPTZ. The seven-day window is computed from receipt time, and without a time zone the demo machine and the server drift by a day.
--   No foreign keys inside this database, matching the platform database style
--
-- Products are split into two mall-standard layers: t_product is the style (SPU), t_product_sku is the orderable configuration (SKU).
-- Specs do not get their own dimension table. specs JSONB holds them: values that do not vary with capacity or color belong to the style; multi-valued ones belong to the configuration

-- ============================================
-- Products
-- ============================================

CREATE TABLE IF NOT EXISTS t_product
(
    id            BIGINT         NOT NULL PRIMARY KEY,
    spu_code      VARCHAR(40)    NOT NULL,
    name          VARCHAR(128)   NOT NULL,
    category      VARCHAR(32)    NOT NULL,
    sub_category  VARCHAR(32),
    brand         VARCHAR(32)    NOT NULL,
    brand_owner   VARCHAR(16)    NOT NULL,
    status        VARCHAR(16)    NOT NULL,
    specs         JSONB          NOT NULL,
    tags          VARCHAR(256),
    selling_point VARCHAR(256),
    create_time   TIMESTAMPTZ    NOT NULL,
    update_time   TIMESTAMPTZ    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_spu_code ON t_product (spu_code);
COMMENT ON TABLE t_product IS '商品款（SPU），一款对应知识库里的一篇商品详情';
COMMENT ON COLUMN t_product.id IS '代理主键';
COMMENT ON COLUMN t_product.spu_code IS '款编码，与知识库商品详情文档同源命名';
COMMENT ON COLUMN t_product.name IS '款名称，不含容量颜色';
COMMENT ON COLUMN t_product.category IS '一级品类，商品筛选的枚举来源';
COMMENT ON COLUMN t_product.sub_category IS '二级品类';
COMMENT ON COLUMN t_product.brand IS '品牌名';
COMMENT ON COLUMN t_product.brand_owner IS '品牌归属。比特严选是授权经销商，不做自营品牌，所以只有代理与第三方两种';
COMMENT ON COLUMN t_product.status IS '整款下架，单个配置下架看 t_product_sku.status';
COMMENT ON COLUMN t_product.specs IS '款级公共属性，如 {"屏幕":"6.3 英寸","芯片":"A20 Pro"}。不随容量颜色变化的规格都归这里，不要复制到每个配置上';
COMMENT ON COLUMN t_product.tags IS '标签，逗号分隔，供按需求筛选';
COMMENT ON COLUMN t_product.selling_point IS '一句话卖点。配件不写，一整个品类共用一句等于没说';
COMMENT ON COLUMN t_product.create_time IS '创建时间';
COMMENT ON COLUMN t_product.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS t_product_sku
(
    id          BIGINT         NOT NULL PRIMARY KEY,
    sku_code    VARCHAR(32)    NOT NULL,
    spu_code    VARCHAR(40)    NOT NULL,
    name        VARCHAR(128)   NOT NULL,
    specs       JSONB          NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    stock       INT            NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    create_time TIMESTAMPTZ    NOT NULL,
    update_time TIMESTAMPTZ    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sku_code ON t_product_sku (sku_code);
COMMENT ON TABLE t_product_sku IS '可下单配置（SKU），下单、加购、扣库存都落在这一层';
COMMENT ON COLUMN t_product_sku.id IS '代理主键';
COMMENT ON COLUMN t_product_sku.sku_code IS '下单编码，取商品的 partNumber';
COMMENT ON COLUMN t_product_sku.spu_code IS '所属商品款';
COMMENT ON COLUMN t_product_sku.name IS '含容量颜色的完整商品名，回给用户时用它';
COMMENT ON COLUMN t_product_sku.specs IS '区分配置的规格键值，如 {"容量":"256GB","颜色":"黑色"}。只放同款下有多个取值的项，单值项属于款、归 t_product.specs。jsonb 不保留写入键序，展示顺序由 BitToolSupport.specs 定';
COMMENT ON COLUMN t_product_sku.price IS '现价，商品价格的唯一真值';
COMMENT ON COLUMN t_product_sku.stock IS '可售库存，下单时条件 UPDATE 扣减';
COMMENT ON COLUMN t_product_sku.status IS '单个配置的上下架';
COMMENT ON COLUMN t_product_sku.create_time IS '创建时间';
COMMENT ON COLUMN t_product_sku.update_time IS '更新时间';

-- ============================================
-- Orders
-- ============================================

CREATE TABLE IF NOT EXISTS t_order
(
    id               BIGINT         NOT NULL PRIMARY KEY,
    order_no         VARCHAR(32)    NOT NULL,
    user_id          VARCHAR(20)    NOT NULL,
    status           VARCHAR(16)    NOT NULL,
    total_amount     NUMERIC(10, 2) NOT NULL,
    discount_amount  NUMERIC(10, 2) NOT NULL,
    pay_amount       NUMERIC(10, 2) NOT NULL,
    coupon_code      VARCHAR(32),
    receiver_name    VARCHAR(32),
    receiver_phone   VARCHAR(20),
    receiver_address VARCHAR(256),
    tracking_no      VARCHAR(32),
    create_time      TIMESTAMPTZ    NOT NULL,
    update_time      TIMESTAMPTZ    NOT NULL,
    pay_time         TIMESTAMPTZ,
    ship_time        TIMESTAMPTZ,
    receive_time     TIMESTAMPTZ,
    cancel_time      TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_no ON t_order (order_no);
COMMENT ON TABLE t_order IS '订单头';
COMMENT ON COLUMN t_order.id IS '代理主键';
COMMENT ON COLUMN t_order.order_no IS '订单号，对用户可见，也是订单行与售后单的关联键';
COMMENT ON COLUMN t_order.user_id IS '平台用户 ID，业务库不设跨库外键';
COMMENT ON COLUMN t_order.status IS '前四个是订单变更要分流的状态，已取消是终态';
COMMENT ON COLUMN t_order.total_amount IS '商品总额，按下单时的单价快照算';
COMMENT ON COLUMN t_order.discount_amount IS '优惠额，服务端按券规则现算，不采信模型转述';
COMMENT ON COLUMN t_order.pay_amount IS '实付额 = 总额 - 优惠额';
COMMENT ON COLUMN t_order.coupon_code IS '本单用掉的券码，取消时按它退回';
COMMENT ON COLUMN t_order.receiver_name IS '收货人';
COMMENT ON COLUMN t_order.receiver_phone IS '收货手机号，查询工具返回前打码';
COMMENT ON COLUMN t_order.receiver_address IS '收货地址，查询工具返回时只到区级';
COMMENT ON COLUMN t_order.tracking_no IS '运单号，发货后才有';
COMMENT ON COLUMN t_order.create_time IS '下单时间，超时释放按它判';
COMMENT ON COLUMN t_order.update_time IS '更新时间';
COMMENT ON COLUMN t_order.pay_time IS '支付时间';
COMMENT ON COLUMN t_order.ship_time IS '发货时间';
COMMENT ON COLUMN t_order.receive_time IS '签收时间，七天无理由窗口按它现算';
COMMENT ON COLUMN t_order.cancel_time IS '取消时间，主动取消与超时释放都落这里';

CREATE TABLE IF NOT EXISTS t_order_item
(
    id          BIGINT         NOT NULL PRIMARY KEY,
    order_no    VARCHAR(32)    NOT NULL,
    sku_code    VARCHAR(32)    NOT NULL,
    sku_name    VARCHAR(128)   NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    quantity    INT            NOT NULL,
    create_time TIMESTAMPTZ    NOT NULL,
    update_time TIMESTAMPTZ    NOT NULL
);
COMMENT ON TABLE t_order_item IS '订单行';
COMMENT ON COLUMN t_order_item.id IS '代理主键';
COMMENT ON COLUMN t_order_item.order_no IS '所属订单号';
COMMENT ON COLUMN t_order_item.sku_code IS '买的哪个配置';
COMMENT ON COLUMN t_order_item.sku_name IS '下单时的商品名快照，商品改名不影响历史订单';
COMMENT ON COLUMN t_order_item.price IS '下单时的单价快照';
COMMENT ON COLUMN t_order_item.quantity IS '数量，库存回补按它加回去';
COMMENT ON COLUMN t_order_item.create_time IS '创建时间';
COMMENT ON COLUMN t_order_item.update_time IS '更新时间';

-- ============================================
-- Logistics and after-sales
-- ============================================

CREATE TABLE IF NOT EXISTS t_logistics_trace
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    tracking_no VARCHAR(32)  NOT NULL,
    trace_time  TIMESTAMPTZ  NOT NULL,
    location    VARCHAR(64),
    description VARCHAR(256) NOT NULL,
    create_time TIMESTAMPTZ  NOT NULL,
    update_time TIMESTAMPTZ  NOT NULL
);
COMMENT ON TABLE t_logistics_trace IS '物流轨迹节点';
COMMENT ON COLUMN t_logistics_trace.id IS '代理主键';
COMMENT ON COLUMN t_logistics_trace.tracking_no IS '运单号，查询时必须联查订单归属，不能只凭它取';
COMMENT ON COLUMN t_logistics_trace.trace_time IS '轨迹发生时间，与 create_time 不是一回事';
COMMENT ON COLUMN t_logistics_trace.location IS '所在地';
COMMENT ON COLUMN t_logistics_trace.description IS '轨迹描述，原样给用户看';
COMMENT ON COLUMN t_logistics_trace.create_time IS '创建时间';
COMMENT ON COLUMN t_logistics_trace.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS t_after_sale
(
    id            BIGINT       NOT NULL PRIMARY KEY,
    after_sale_no VARCHAR(32)  NOT NULL,
    order_no      VARCHAR(32)  NOT NULL,
    sku_code      VARCHAR(32)  NOT NULL,
    type          VARCHAR(16)  NOT NULL,
    reason        VARCHAR(256),
    status        VARCHAR(16)  NOT NULL,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    finish_time   TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_after_sale_no ON t_after_sale (after_sale_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_after_sale_open ON t_after_sale (order_no, sku_code)
    WHERE status IN ('待处理', '处理中');
COMMENT ON TABLE t_after_sale IS '售后单，不回写订单状态：一单多件只退一件时整单置退款中是错的';
COMMENT ON COLUMN t_after_sale.id IS '代理主键';
COMMENT ON COLUMN t_after_sale.after_sale_no IS '售后单号，重复提交时原样回给用户';
COMMENT ON COLUMN t_after_sale.order_no IS '关联订单号';
COMMENT ON COLUMN t_after_sale.sku_code IS '退哪一件，一单多件时只作用于它';
COMMENT ON COLUMN t_after_sale.type IS '退货退款、换货或仅退款。七天窗口过后只受理前两种';
COMMENT ON COLUMN t_after_sale.reason IS '申请原因，取自用户明确说明，不代为编造';
COMMENT ON COLUMN t_after_sale.status IS '售后处理状态';
COMMENT ON COLUMN t_after_sale.create_time IS '申请时间';
COMMENT ON COLUMN t_after_sale.update_time IS '更新时间';
COMMENT ON COLUMN t_after_sale.finish_time IS '处理完成时间';
COMMENT ON INDEX uk_after_sale_open IS '同一单同一件只能有一张未结束的售后单，重复提交靠它挡下并回原单号';

-- ============================================
-- Cart and coupons
-- ============================================

CREATE TABLE IF NOT EXISTS t_cart
(
    id          BIGINT         NOT NULL PRIMARY KEY,
    user_id     VARCHAR(20)    NOT NULL,
    sku_code    VARCHAR(32)    NOT NULL,
    quantity    INT            NOT NULL,
    added_price NUMERIC(10, 2) NOT NULL,
    create_time TIMESTAMPTZ    NOT NULL,
    update_time TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_cart_user_sku UNIQUE (user_id, sku_code)
);
COMMENT ON TABLE t_cart IS '购物车';
COMMENT ON COLUMN t_cart.id IS '代理主键';
COMMENT ON COLUMN t_cart.user_id IS '平台用户 ID';
COMMENT ON COLUMN t_cart.sku_code IS '加的哪个配置，用户不能只说要某款，系统不知道扣哪份库存';
COMMENT ON COLUMN t_cart.quantity IS '数量，写入是设为几件而不是加几件';
COMMENT ON COLUMN t_cart.added_price IS '加购价快照，与商品现价现比得出降价商品，不另建价格历史表';
COMMENT ON COLUMN t_cart.create_time IS '加购时间，设置数量时不刷新，刷新了这件降了多少就永远算成 0';
COMMENT ON COLUMN t_cart.update_time IS '更新时间';
COMMENT ON CONSTRAINT uk_cart_user_sku ON t_cart IS '设置目标数量走 upsert，这条让重发多少次结果都一样';

CREATE TABLE IF NOT EXISTS t_coupon
(
    id             BIGINT         NOT NULL PRIMARY KEY,
    coupon_code    VARCHAR(32)    NOT NULL,
    name           VARCHAR(64)    NOT NULL,
    type           VARCHAR(16)    NOT NULL,
    threshold      NUMERIC(10, 2) NOT NULL,
    discount_value NUMERIC(10, 2) NOT NULL,
    category       VARCHAR(32),
    valid_from     TIMESTAMPTZ    NOT NULL,
    valid_to       TIMESTAMPTZ    NOT NULL,
    create_time    TIMESTAMPTZ    NOT NULL,
    update_time    TIMESTAMPTZ    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_coupon_code ON t_coupon (coupon_code);
COMMENT ON TABLE t_coupon IS '券模板';
COMMENT ON COLUMN t_coupon.id IS '代理主键';
COMMENT ON COLUMN t_coupon.coupon_code IS '券码，用户持券关系按它关联';
COMMENT ON COLUMN t_coupon.name IS '券名，回给用户时连编码一起报';
COMMENT ON COLUMN t_coupon.type IS '满减或折扣，决定 discount_value 怎么解释';
COMMENT ON COLUMN t_coupon.threshold IS '使用门槛金额，0 表示无门槛';
COMMENT ON COLUMN t_coupon.discount_value IS '满减券为减免金额，折扣券为折扣率（0.92 即九二折）';
COMMENT ON COLUMN t_coupon.category IS '适用品类，为空表示全品类。只按金额判会推荐出一张根本用不了的券';
COMMENT ON COLUMN t_coupon.valid_from IS '生效时间';
COMMENT ON COLUMN t_coupon.valid_to IS '失效时间，过没过期按当前时间现判，不落库';
COMMENT ON COLUMN t_coupon.create_time IS '创建时间';
COMMENT ON COLUMN t_coupon.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS t_user_coupon
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     VARCHAR(20) NOT NULL,
    coupon_code VARCHAR(32) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    order_no    VARCHAR(32),
    create_time TIMESTAMPTZ NOT NULL,
    update_time TIMESTAMPTZ NOT NULL,
    use_time    TIMESTAMPTZ,
    CONSTRAINT uk_user_coupon UNIQUE (user_id, coupon_code)
);
COMMENT ON TABLE t_user_coupon IS '用户持券关系';
COMMENT ON COLUMN t_user_coupon.id IS '代理主键';
COMMENT ON COLUMN t_user_coupon.user_id IS '持券人';
COMMENT ON COLUMN t_user_coupon.coupon_code IS '券码';
COMMENT ON COLUMN t_user_coupon.status IS '只说有没有被用掉。核销走条件 UPDATE 判受影响行数，防并发重复核销';
COMMENT ON COLUMN t_user_coupon.order_no IS '核销到哪一单，取消订单时按它退回';
COMMENT ON COLUMN t_user_coupon.create_time IS '领取时间';
COMMENT ON COLUMN t_user_coupon.update_time IS '更新时间';
COMMENT ON COLUMN t_user_coupon.use_time IS '核销时间，退回时清空';

-- ============================================
-- Manual tickets
-- ============================================

CREATE TABLE IF NOT EXISTS t_ticket
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    ticket_no   VARCHAR(32) NOT NULL,
    user_id     VARCHAR(20) NOT NULL,
    category    VARCHAR(32) NOT NULL,
    content     TEXT        NOT NULL,
    status      VARCHAR(16) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL,
    update_time TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ticket_no ON t_ticket (ticket_no);
COMMENT ON TABLE t_ticket IS '转人工工单，是办不下去时的逃生门，不算一项业务任务';
COMMENT ON COLUMN t_ticket.id IS '代理主键';
COMMENT ON COLUMN t_ticket.ticket_no IS '工单号，按天累计流水，提交后要告知用户';
COMMENT ON COLUMN t_ticket.user_id IS '提单人';
COMMENT ON COLUMN t_ticket.category IS '工单分类';
COMMENT ON COLUMN t_ticket.content IS '工单内容，要带上已经确认过的信息，人工不用再问一遍';
COMMENT ON COLUMN t_ticket.status IS '受理状态';
COMMENT ON COLUMN t_ticket.create_time IS '提交时间';
COMMENT ON COLUMN t_ticket.update_time IS '更新时间';
