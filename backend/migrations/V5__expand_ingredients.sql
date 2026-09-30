-- V5__expand_ingredients.sql
-- 扩充配菜库：7大分类下各增加10-20种常见食材

-- 蔬菜类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('胡萝卜',  (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  5.00, '🥕'),
('青椒',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  6.00, '🫑'),
('洋葱',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  4.00, '🧅'),
('茄子',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  5.00, '🍆'),
('豆角',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  6.00, '🫛'),
('四季豆', (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  7.00, '🫘'),
('玉米',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  5.00, '🌽'),
('莲藕',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  8.00, '🥒'),
('金针菇', (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  6.00, '🍄'),
('木耳',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  5.00, '🌿'),
('菠菜',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  5.00, '🥬'),
('生菜',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  4.00, '🥬'),
('黄瓜',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  4.00, '🥒'),
('冬瓜',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  3.00, '🎃'),
('南瓜',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  4.00, '🎃'),
('蒜苔',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  6.00, '🧄'),
('茭白',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  8.00, '🌾'),
('芋头',   (SELECT id FROM t_ingredient_category WHERE name='蔬菜'), '克',  5.00, '🍠');

-- 蛋奶类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('咸鸭蛋', (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '个',  3.00, '🥚'),
('皮蛋',   (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '个',  4.00, '🥚'),
('奶酪',   (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '克', 20.00, '🧀'),
('淡奶油', (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '克',  8.00, '🥛'),
('酸奶',   (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '瓶', 10.00, '🥛'),
('鹌鹑蛋', (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '个',  0.80, '🥚'),
('鸭蛋',   (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '个',  2.50, '🥚'),
('鹅蛋',   (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '个',  5.00, '🥚'),
('芝士片', (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '片',  3.00, '🧀'),
('黄油',   (SELECT id FROM t_ingredient_category WHERE name='蛋奶'), '克', 12.00, '🧈');

-- 肉禽类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('牛肉',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 40.00, '🥩'),
('羊肉',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 45.00, '🥩'),
('腊肉',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 35.00, '🥓'),
('广式香肠',(SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 30.00, '🌭'),
('腊肠',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 28.00, '🌭'),
('火腿',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 25.00, '🥓'),
('培根',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 22.00, '🥓'),
('牛腩',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 42.00, '🥩'),
('牛百叶', (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 30.00, '🐄'),
('猪蹄',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 28.00, '🐖'),
('猪肝',   (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 20.00, '🐖'),
('肥牛片', (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 38.00, '🥩'),
('五花肉', (SELECT id FROM t_ingredient_category WHERE name='肉禽'), '克', 28.00, '🥓');

-- 海鲜类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('蟹',     (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '只', 25.00, '🦀'),
('蛤蜊',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 15.00, '🐚'),
('鱿鱼',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 25.00, '🦑'),
('带鱼',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 20.00, '🐟'),
('鲫鱼',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 18.00, '🐟'),
('鳊鱼',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 16.00, '🐟'),
('鲢鱼',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 15.00, '🐟'),
('海带',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 4.00, '🌿'),
('紫菜',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 5.00, '🌿'),
('小龙虾', (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 30.00, '🦞'),
('花甲',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 12.00, '🐚'),
('鲍鱼',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '只', 15.00, '🐚'),
('生蚝',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '只', 10.00, '🦪'),
('海参',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '条', 30.00, '🌊'),
('蛏子',   (SELECT id FROM t_ingredient_category WHERE name='海鲜'), '克', 14.00, '🐚');

-- 调料类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('白砂糖', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.50, '🍬'),
('白醋',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.30, '🍶'),
('料酒',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.50, '🍶'),
('蚝油',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  1.00, '🥣'),
('郫县豆瓣酱',(SELECT id FROM t_ingredient_category WHERE name='调料'), '勺', 1.50, '🫙'),
('辣椒酱', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  1.00, '🌶️'),
('花椒粉', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.50, '🌶️'),
('八角',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '个',  0.30, '🍃'),
('桂皮',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '块',  0.30, '🍃'),
('香叶',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '片',  0.20, '🍃'),
('五香粉', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.50, '🧂'),
('白胡椒粉',(SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.50, '🌶️'),
('黑芝麻', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  1.00, '⚫'),
('芝麻酱', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  2.00, '🥜'),
('蒜末',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.30, '🧄'),
('姜末',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.30, '🥒'),
('葱花',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.30, '🧅'),
('剁椒',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  1.00, '🌶️'),
('番茄酱', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.80, '🍅'),
('沙拉酱', (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  1.50, '🥗'),
('蜂蜜',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  2.00, '🍯'),
('生抽',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.60, '🥫'),
('老抽',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.60, '🥫'),
('蒸鱼豉油',(SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  1.00, '🐟'),
('鸡精',   (SELECT id FROM t_ingredient_category WHERE name='调料'), '勺',  0.50, '🍗');

-- 主食类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('面粉',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  4.00, '🌾'),
('淀粉',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  3.00, '🌾'),
('红薯',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  4.00, '🍠'),
('紫薯',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  5.00, '🍠'),
('糯米',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  6.00, '🍚'),
('饺子皮', (SELECT id FROM t_ingredient_category WHERE name='主食'), '张',  0.80, '🥟'),
('包子',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '个',  3.00, '🥮'),
('馒头',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '个',  2.00, '🥖'),
('粉丝',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  5.00, '🍜'),
('粉条',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  5.00, '🍝'),
('年糕',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '克',  6.00, '🍡'),
('手抓饼', (SELECT id FROM t_ingredient_category WHERE name='主食'), '张',  3.00, '🫓'),
('烧饼',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '个',  2.50, '🥙'),
('油条',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '根',  2.00, '🥖'),
('煎饼',   (SELECT id FROM t_ingredient_category WHERE name='主食'), '张',  5.00, '🥞');

-- 水果类扩充
INSERT INTO t_ingredient (name, category_id, unit, price, emoji) VALUES
('橙子',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  6.00, '🍊'),
('葡萄',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 15.00, '🍇'),
('草莓',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 30.00, '🍓'),
('西瓜',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克',  3.00, '🍉'),
('梨',     (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  5.00, '🍐'),
('桃子',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  7.00, '🍑'),
('芒果',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 15.00, '🥭'),
('猕猴桃', (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  4.00, '🥝'),
('火龙果', (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  8.00, '🐉'),
('柚子',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '瓣',  3.00, '🍊'),
('橘子',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  4.00, '🍊'),
('荔枝',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 20.00, '荔枝'),
('龙眼',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 18.00, '🐉'),
('樱桃',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 35.00, '🍒'),
('蓝莓',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克', 30.00, '🫐'),
('哈密瓜', (SELECT id FROM t_ingredient_category WHERE name='水果'), '克',  5.00, '🍈'),
('甘蔗',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '节',  5.00, '🎋'),
('菠萝',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '克',  6.00, '🍍'),
('椰子',   (SELECT id FROM t_ingredient_category WHERE name='水果'), '个', 12.00, '🥥'),
('百香果', (SELECT id FROM t_ingredient_category WHERE name='水果'), '个',  4.00, '🫣');
