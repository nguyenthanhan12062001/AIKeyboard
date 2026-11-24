package com.ai.banphim.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java offline Simplified Chinese Pinyin Input Engine.
 * Supports maximal-forward syllable segmentation, multi-syllable phrase matching,
 * single-syllable Hanzi selection (allowing partial buffer consumption), and prefix completions.
 */
public final class PinyinEngine {

    /** Single Hanzi or phrase suggestion paired with how many raw Pinyin letters it consumes. */
    public static final class Candidate {
        public final String text;
        public final int consumedRawLength;

        /** Creates a Pinyin conversion candidate. */
        public Candidate(String text, int consumedRawLength) {
            this.text = text;
            this.consumedRawLength = consumedRawLength;
        }
    }

    /** Segmented Pinyin lookup result containing formatted syllables and ranked candidates. */
    public static final class LookupResult {
        public final String rawInput;
        public final String segmentedDisplay;
        public final List<Candidate> candidates;

        /** Creates an immutable lookup result. */
        public LookupResult(String rawInput, String segmentedDisplay, List<Candidate> candidates) {
            this.rawInput = rawInput;
            this.segmentedDisplay = segmentedDisplay;
            this.candidates = Collections.unmodifiableList(candidates);
        }
    }

    private static final class SegmentSpan {
        final String syllable;
        final int endRawIndex;

        SegmentSpan(String syllable, int endRawIndex) {
            this.syllable = syllable;
            this.endRawIndex = endRawIndex;
        }
    }

    private static final Map<String, List<String>> PHRASES = new HashMap<>();
    private static final Map<String, List<String>> SYLLABLES = new HashMap<>();

    static {
        initSyllables();
        initPhrases();
    }

    private PinyinEngine() {}

    /**
     * Segments raw Pinyin input into syllables and resolves matching phrases, single characters,
     * and an ASCII fallback option.
     */
    public static LookupResult lookup(String rawInput) {
        if (rawInput == null || rawInput.isEmpty()) {
            return new LookupResult("", "", Collections.emptyList());
        }

        String normalized = rawInput.toLowerCase();
        List<SegmentSpan> spans = segment(normalized);
        StringBuilder segDisplay = new StringBuilder();
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0) segDisplay.append('\'');
            segDisplay.append(spans.get(i).syllable);
        }

        String compactKey = normalized.replace("'", "");
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // 1. Exact full phrase match
        List<String> exactPhrases = PHRASES.get(compactKey);
        if (exactPhrases != null) {
            for (String p : exactPhrases) {
                if (seen.add(p)) {
                    out.add(new Candidate(p, rawInput.length()));
                }
            }
        }

        // 2. Prefix phrase matches when typing multi-syllable or partial pinyin
        if (compactKey.length() >= 2 && spans.size() >= 1) {
            int prefixCount = 0;
            for (Map.Entry<String, List<String>> entry : PHRASES.entrySet()) {
                if (entry.getKey().startsWith(compactKey) && !entry.getKey().equals(compactKey)) {
                    for (String p : entry.getValue()) {
                        if (seen.add(p)) {
                            out.add(new Candidate(p, rawInput.length()));
                            prefixCount++;
                            if (prefixCount >= 4) break;
                        }
                    }
                }
                if (prefixCount >= 4) break;
            }
        }

        // 3. Auto-composed multi-syllable word if multiple valid syllables are present
        if (spans.size() >= 2) {
            // Check sub-phrase prefix (e.g. first 2 syllables)
            String firstTwoKey = spans.get(0).syllable + spans.get(1).syllable;
            List<String> subPhrases = PHRASES.get(firstTwoKey);
            if (subPhrases != null) {
                for (String p : subPhrases) {
                    if (seen.add(p)) {
                        out.add(new Candidate(p, spans.get(1).endRawIndex));
                    }
                }
            }

            StringBuilder autoComposed = new StringBuilder();
            int consumed = 0;
            boolean allMatched = true;
            for (SegmentSpan sp : spans) {
                List<String> chars = SYLLABLES.get(sp.syllable);
                if (chars != null && !chars.isEmpty()) {
                    autoComposed.append(chars.get(0));
                    consumed = sp.endRawIndex;
                } else {
                    allMatched = false;
                    break;
                }
            }
            if (allMatched && autoComposed.length() >= 2 && seen.add(autoComposed.toString())) {
                out.add(new Candidate(autoComposed.toString(), consumed));
            }
        }

        // 4. First syllable characters (allows character-by-character picking inside long pinyin)
        if (!spans.isEmpty()) {
            SegmentSpan first = spans.get(0);
            List<String> firstChars = SYLLABLES.get(first.syllable);
            if (firstChars != null) {
                for (String ch : firstChars) {
                    if (seen.add(ch)) {
                        out.add(new Candidate(ch, first.endRawIndex));
                    }
                }
            } else {
                // Prefix match on single syllable if user is mid-syllable (e.g. "zh" -> 这, 中...)
                int count = 0;
                for (Map.Entry<String, List<String>> entry : SYLLABLES.entrySet()) {
                    if (entry.getKey().startsWith(first.syllable)) {
                        for (String ch : entry.getValue()) {
                            if (seen.add(ch)) {
                                out.add(new Candidate(ch, rawInput.length()));
                                count++;
                                if (count >= 8) break;
                            }
                        }
                    }
                    if (count >= 8) break;
                }
            }
        }

        // 5. Fallback raw ASCII candidate so user can keep Latin words if desired
        if (seen.add(rawInput)) {
            out.add(new Candidate(rawInput, rawInput.length()));
        }

        return new LookupResult(rawInput, segDisplay.toString(), out);
    }

    private static List<SegmentSpan> segment(String input) {
        List<SegmentSpan> result = new ArrayList<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            if (input.charAt(i) == '\'') {
                i++;
                continue;
            }
            int maxLen = Math.min(6, n - i);
            String matched = null;
            int matchedLen = 0;
            for (int len = maxLen; len >= 1; len--) {
                String sub = input.substring(i, i + len);
                if (sub.indexOf('\'') >= 0) continue;
                if (SYLLABLES.containsKey(sub)) {
                    matched = sub;
                    matchedLen = len;
                    break;
                }
            }
            if (matched != null) {
                i += matchedLen;
                result.add(new SegmentSpan(matched, i));
            } else {
                // Unfinished partial syllable: consume until next apostrophe or end
                int end = i + 1;
                while (end < n && input.charAt(end) != '\'' && !SYLLABLES.containsKey(input.substring(end))) {
                    // Check if a valid syllable starts later at end
                    boolean validAhead = false;
                    for (int k = Math.min(6, n - end); k >= 2; k--) {
                        if (SYLLABLES.containsKey(input.substring(end, end + k))) {
                            validAhead = true;
                            break;
                        }
                    }
                    if (validAhead) break;
                    end++;
                }
                result.add(new SegmentSpan(input.substring(i, end), end));
                i = end;
            }
        }
        return result;
    }

    private static void syl(String pinyin, String... chars) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, chars);
        SYLLABLES.put(pinyin, list);
    }

    private static void phr(String pinyin, String... words) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, words);
        PHRASES.put(pinyin, list);
    }

    private static void initPhrases() {
        phr("nihao", "你好", "您好");
        phr("nihaoma", "你好吗");
        phr("ninhao", "您好");
        phr("xiexie", "谢谢", "感谢");
        phr("xiexieni", "谢谢你");
        phr("bukeqi", "不客气");
        phr("zaijian", "再见");
        phr("duibuqi", "对不起");
        phr("meiguanxi", "没关系");
        phr("qingwen", "请问");
        phr("baogian", "抱歉");
        phr("baoqian", "抱歉");
        phr("zaoshanghao", "早上好");
        phr("wanshanghao", "晚上好");
        phr("wanan", "晚安");
        phr("woaini", "我爱你");
        phr("woxiangni", "我想你");
        phr("women", "我们");
        phr("nimen", "你们");
        phr("tamen", "他们", "她们", "它们");
        phr("woshi", "我是");
        phr("nishi", "你是");
        phr("tashi", "他是", "她是");
        phr("zheshi", "这是");
        phr("nashi", "那是");
        phr("shenme", "什么");
        phr("weishenme", "为什么");
        phr("zenme", "怎么");
        phr("zenmeyang", "怎么样");
        phr("duoshao", "多少");
        phr("duoshaoqian", "多少钱");
        phr("nali", "哪里");
        phr("zheli", "这里");
        phr("keyi", "可以");
        phr("bukeyi", "不可以");
        phr("meiyou", "没有");
        phr("zhidao", "知道");
        phr("buzhidao", "不知道");
        phr("renshi", "认识");
        phr("xihuan", "喜欢");
        phr("gaoxing", "高兴");
        phr("kaixin", "开心");
        phr("kuaile", "快乐");
        phr("jiayou", "加油");
        phr("pengyou", "朋友");
        phr("haopengyou", "好朋友");
        phr("jiaren", "家人");
        phr("baba", "爸爸");
        phr("mama", "妈妈");
        phr("gege", "哥哥");
        phr("jiejie", "姐姐");
        phr("didi", "弟弟");
        phr("meimei", "妹妹");
        phr("laoshi", "老师");
        phr("xuesheng", "学生");
        phr("tongxue", "同学");
        phr("tongshi", "同事");
        phr("yisheng", "医生");
        phr("zhongguo", "中国");
        phr("beijing", "北京");
        phr("shanghai", "上海");
        phr("guangzhou", "广州");
        phr("shenzhen", "深圳");
        phr("xian", "西安", "先", "现", "线");
        phr("yuenan", "越南");
        phr("henei", "河内");
        phr("meiguo", "美国");
        phr("riben", "日本");
        phr("hanguo", "韩国");
        phr("yingguo", "英国");
        phr("faguo", "法国");
        phr("deguo", "德国");
        phr("zhongwen", "中文");
        phr("hanyu", "汉语", "韩语");
        phr("putonghua", "普通话");
        phr("yingyu", "英语");
        phr("yuenanyu", "越南语");
        phr("jintian", "今天");
        phr("mingtian", "明天");
        phr("zuotian", "昨天");
        phr("xianzai", "现在");
        phr("shijian", "时间");
        phr("difang", "地方");
        phr("dongxi", "东西");
        phr("wenti", "问题");
        phr("banfa", "办法");
        phr("yisi", "意思");
        phr("chifan", "吃饭");
        phr("heshui", "喝水");
        phr("shuijiao", "睡觉");
        phr("gongzuo", "工作");
        phr("xuexi", "学习");
        phr("shouji", "手机");
        phr("diannao", "电脑");
        phr("dianhua", "电话");
        phr("jianpan", "键盘");
        phr("fanyi", "翻译");
        phr("bangzhu", "帮助");
        phr("xiwang", "希望");
        phr("xiangxin", "相信");
        phr("haochi", "好吃");
        phr("haokan", "好看");
        phr("haoting", "好听");
        phr("piaoliang", "漂亮");
        phr("meili", "美丽");
        phr("kafei", "咖啡");
        phr("naicha", "奶茶");
        phr("miantiao", "面条");
        phr("mifan", "米饭");
        phr("shui", "水", "谁", "睡", "税");
        phr("dianying", "电影");
        phr("yinyue", "音乐");
        phr("youxi", "游戏");
        phr("shijie", "世界");
        phr("shengri", "生日");
        phr("shengrikuaile", "生日快乐");
        phr("xinnian", "新年");
        phr("xinniankuaile", "新年快乐");
        phr("gongxifacai", "恭喜发财");
        phr("yibian", "一边", "一遍");
        phr("yiqi", "一起");
        phr("yixia", "一下");
        phr("yidian", "一点");
        phr("yiding", "一定");
        phr("yijing", "已经");
        phr("yinwei", "因为");
        phr("suoyi", "所以");
        phr("danshi", "但是");
        phr("ruguo", "如果");
        phr("suiran", "虽然");
        phr("huozhe", "或者");
        phr("haishi", "还是");
        phr("feichang", "非常");
        phr("tebie", "特别");
        phr("zhende", "真的");
        phr("haode", "好的");
        phr("meishi", "没事");
        phr("dengyixia", "等一下");
        phr("mashang", "马上");
    }

    private static void initSyllables() {
        syl("a", "啊", "阿", "呀");
        syl("ai", "爱", "矮", "唉", "哀");
        syl("an", "安", "按", "暗", "案", "岸");
        syl("ang", "昂", "仰");
        syl("ao", "奥", "傲", "熬", "澳");
        syl("ba", "吧", "把", "八", "爸", "巴", "拔");
        syl("bai", "白", "百", "摆", "败", "拜");
        syl("ban", "办", "半", "班", "板", "版", "搬");
        syl("bang", "帮", "棒", "榜", "绑");
        syl("bao", "包", "报", "保", "宝", "抱", "饱", "爆");
        syl("bei", "被", "北", "备", "背", "杯", "悲");
        syl("ben", "本", "笨", "奔");
        syl("beng", "蹦", "崩");
        syl("bi", "比", "必", "笔", "鼻", "闭", "币", "避");
        syl("bian", "边", "变", "便", "遍", "编", "辩");
        syl("biao", "表", "标", "飙");
        syl("bie", "别", "憋");
        syl("bin", "宾", "滨");
        syl("bing", "并", "病", "冰", "兵", "饼");
        syl("bo", "波", "播", "伯", "博", "脖");
        syl("bu", "不", "部", "步", "布", "补", "捕");
        syl("ca", "擦");
        syl("cai", "才", "菜", "猜", "采", "材", "财");
        syl("can", "参", "餐", "残", "惨");
        syl("cang", "藏", "仓", "苍");
        syl("cao", "草", "操", "槽");
        syl("ce", "测", "策", "侧", "册");
        syl("ceng", "曾", "层");
        syl("cha", "茶", "差", "查", "插", "察");
        syl("chai", "拆", "柴");
        syl("chan", "产", "缠", "禅");
        syl("chang", "长", "常", "场", "唱", "厂", "肠");
        syl("chao", "超", "朝", "吵", "炒", "潮");
        syl("che", "车", "撤", "彻");
        syl("chen", "沉", "晨", "陈", "称", "衬");
        syl("cheng", "成", "城", "程", "乘", "称", "承", "诚");
        syl("chi", "吃", "持", "迟", "尺", "齿", "池");
        syl("chong", "重", "冲", "充", "虫", "宠");
        syl("chou", "抽", "丑", "臭", "愁");
        syl("chu", "出", "初", "处", "除", "楚", "触");
        syl("chuan", "穿", "传", "船", "串", "川");
        syl("chuang", "床", "窗", "创", "闯");
        syl("chui", "吹", "垂", "锤");
        syl("chun", "春", "纯", "唇");
        syl("chuo", "戳");
        syl("ci", "次", "此", "词", "辞", "磁", "刺");
        syl("cong", "从", "聪", "匆", "葱");
        syl("cou", "凑");
        syl("cu", "粗", "促", "醋");
        syl("cuan", "窜");
        syl("cui", "催", "脆", "翠");
        syl("cun", "村", "存", "寸");
        syl("cuo", "错", "措", "搓");
        syl("da", "大", "打", "答", "达", "搭");
        syl("dai", "带", "代", "待", "袋", "戴", "呆");
        syl("dan", "但", "单", "蛋", "担", "淡", "胆", "弹");
        syl("dang", "当", "党", "挡", "档");
        syl("dao", "到", "道", "倒", "刀", "导", "岛", "盗");
        syl("de", "的", "得", "德");
        syl("dei", "得");
        syl("deng", "等", "灯", "登", "瞪");
        syl("di", "地", "第", "低", "底", "弟", "敌", "帝", "递");
        syl("dian", "点", "电", "店", "典", "垫", "掉");
        syl("diao", "掉", "调", "吊", "钓", "雕");
        syl("die", "跌", "叠", "爹", "蝶");
        syl("ding", "定", "订", "顶", "盯", "钉");
        syl("diu", "丢");
        syl("dong", "东", "动", "懂", "冬", "冻", "洞");
        syl("dou", "都", "斗", "豆", "抖", "逗");
        syl("du", "读", "度", "都", "独", "毒", "赌", "肚");
        syl("duan", "短", "段", "断", "端");
        syl("dui", "对", "队", "堆", "兑");
        syl("dun", "顿", "盾", "蹲", "吨");
        syl("duo", "多", "朵", "躲", "夺");
        syl("e", "饿", "额", "恶", "鹅", "俄");
        syl("en", "嗯", "恩");
        syl("er", "二", "而", "儿", "耳", "尔");
        syl("fa", "发", "法", "罚", "乏");
        syl("fan", "反", "饭", "翻", "烦", "繁", "范", "犯");
        syl("fang", "放", "方", "房", "防", "访", "仿");
        syl("fei", "飞", "非", "费", "肥", "肺", "废");
        syl("fen", "分", "粉", "份", "奋", "愤", "纷");
        syl("feng", "风", "封", "丰", "疯", "峰", "逢", "凤");
        syl("fo", "佛");
        syl("fou", "否");
        syl("fu", "服", "福", "付", "父", "夫", "富", "复", "负", "副");
        syl("ga", "嘎", "尬");
        syl("gai", "该", "改", "盖", "概");
        syl("gan", "感", "干", "敢", "赶", "甘", "肝");
        syl("gang", "刚", "钢", "港", "岗", "缸");
        syl("gao", "高", "告", "搞", "稿", "糕");
        syl("ge", "个", "各", "歌", "哥", "格", "隔", "割");
        syl("gei", "给");
        syl("gen", "跟", "根");
        syl("geng", "更", "耕", "颈");
        syl("gong", "工", "公", "共", "功", "供", "攻", "宫", "恭");
        syl("gou", "够", "狗", "购", "构", "沟", "勾");
        syl("gu", "古", "故", "固", "姑", "骨", "鼓", "谷", "顾");
        syl("gua", "挂", "瓜", "刮", "寡");
        syl("guai", "怪", "乖", "拐");
        syl("guan", "关", "管", "官", "观", "惯", "馆", "冠");
        syl("guang", "光", "广", "逛");
        syl("gui", "贵", "鬼", "规", "归", "柜", "跪");
        syl("gun", "滚", "棍");
        syl("guo", "过", "国", "果", "锅", "裹");
        syl("ha", "哈");
        syl("hai", "还", "海", "害", "孩", "嗨");
        syl("han", "汉", "韩", "寒", "含", "喊", "汗", "旱");
        syl("hang", "行", "航", "杭");
        syl("hao", "好", "号", "毫", "耗", "豪");
        syl("he", "和", "喝", "合", "河", "何", "盒", "核", "贺");
        syl("hei", "黑", "嘿");
        syl("hen", "很", "狠", "恨", "痕");
        syl("heng", "横", "衡", "哼", "恒");
        syl("hong", "红", "轰", "洪", "宏", "虹");
        syl("hou", "后", "候", "厚", "猴", "喉");
        syl("hu", "护", "湖", "虎", "呼", "互", "户", "胡", "糊");
        syl("hua", "花", "话", "画", "化", "华", "滑", "划");
        syl("huai", "坏", "怀", "准");
        syl("huan", "换", "欢", "还", "环", "缓", "患");
        syl("huang", "黄", "皇", "慌", "荒", "晃", "谎");
        syl("hui", "会", "回", "灰", "汇", "挥", "恢", "悔", "毁");
        syl("hun", "婚", "混", "魂", "昏");
        syl("huo", "活", "火", "或", "货", "获", "伙", "祸");
        syl("ji", "几", "机", "己", "记", "急", "集", "及", "极", "计", "寄", "鸡");
        syl("jia", "家", "加", "假", "价", "架", "驾", "甲");
        syl("jian", "见", "件", "间", "简", "建", "健", "减", "剑", "尖", "兼");
        syl("jiang", "讲", "将", "江", "奖", "降", "酱", "疆");
        syl("jiao", "叫", "教", "交", "角", "脚", "觉", "较", "焦", "胶");
        syl("jie", "接", "姐", "结", "借", "解", "界", "节", "街", "介", "届");
        syl("jin", "进", "今", "近", "金", "紧", "仅", "尽", "禁", "斤");
        syl("jing", "经", "京", "精", "静", "惊", "镜", "警", "竟", "竞", "净");
        syl("jiong", "窘");
        syl("jiu", "就", "九", "久", "酒", "救", "旧", "究");
        syl("ju", "句", "局", "举", "具", "居", "剧", "拒", "聚", "巨", "距");
        syl("juan", "卷", "捐", "倦");
        syl("jue", "觉", "决", "绝", "角", "掘");
        syl("jun", "军", "均", "菌", "君", "俊");
        syl("ka", "卡", "咖");
        syl("kai", "开", "凯", "慨");
        syl("kan", "看", "砍", "刊", "堪");
        syl("kang", "康", "抗", "扛");
        syl("kao", "考", "靠", "烤");
        syl("ke", "可", "科", "课", "客", "刻", "克", "颗", "渴");
        syl("ken", "肯", "啃");
        syl("keng", "坑");
        syl("kong", "空", "控", "恐", "孔");
        syl("kou", "口", "扣", "抠");
        syl("ku", "哭", "苦", "库", "酷", "裤");
        syl("kua", "夸", "跨", "垮");
        syl("kuai", "快", "块", "筷", "会");
        syl("kuan", "宽", "款");
        syl("kuang", "况", "狂", "矿", "框", "筐");
        syl("kui", "亏", "愧", "盔");
        syl("kun", "困", "昆", "捆");
        syl("kuo", "扩", "阔");
        syl("la", "啦", "拉", "辣", "蜡", "腊");
        syl("lai", "来", "赖");
        syl("lan", "蓝", "懒", "烂", "兰", "拦", "栏", "篮");
        syl("lang", "狼", "浪", "郎", "朗", "廊");
        syl("lao", "老", "劳", "牢", "捞", "烤");
        syl("le", "了", "乐");
        syl("lei", "累", "类", "泪", "雷");
        syl("leng", "冷", "愣");
        syl("li", "里", "理", "力", "立", "离", "礼", "历", "利", "例", "丽", "李");
        syl("lia", "俩");
        syl("lian", "连", "脸", "练", "联", "恋", "怜", "链");
        syl("liang", "两", "亮", "量", "凉", "粮", "良", "辆");
        syl("liao", "聊", "了", "料", "疗", "辽");
        syl("lie", "列", "烈", "裂", "猎");
        syl("lin", "林", "临", "邻", "淋", "磷");
        syl("ling", "零", "领", "另", "灵", "令", "铃", "龄");
        syl("liu", "六", "流", "留", "刘", "柳", "溜");
        syl("long", "龙", "笼", "隆");
        syl("lou", "楼", "漏", "露");
        syl("lu", "路", "录", "陆", "露", "炉", "鹿", "鲁");
        syl("lv", "绿", "旅", "律", "率", "虑", "滤", "驴");
        syl("luan", "乱", "卵");
        syl("lue", "略");
        syl("lun", "论", "轮", "伦");
        syl("luo", "落", "罗", "逻", "络", "螺");
        syl("ma", "吗", "妈", "马", "麻", "骂", "码", "嘛");
        syl("mai", "买", "卖", "迈", "麦", "埋", "脉");
        syl("man", "慢", "满", "漫", "蛮", "瞒");
        syl("mang", "忙", "盲", "茫");
        syl("mao", "猫", "毛", "帽", "冒", "貌", "茂", "贸");
        syl("me", "么");
        syl("mei", "没", "美", "每", "妹", "煤", "梅", "眉");
        syl("men", "们", "门", "闷");
        syl("meng", "梦", "猛", "蒙", "盟", "萌");
        syl("mi", "米", "迷", "密", "秘", "蜜", "谜");
        syl("mian", "面", "免", "棉", "眠", "勉");
        syl("miao", "秒", "妙", "描", "庙", "苗");
        syl("mie", "灭");
        syl("min", "民", "敏");
        syl("ming", "明", "名", "命", "鸣");
        syl("miu", "谬");
        syl("mo", "摸", "模", "末", "莫", "墨", "魔", "磨", "默");
        syl("mou", "某", "谋");
        syl("mu", "母", "木", "目", "幕", "墓", "牧", "慕");
        syl("na", "那", "拿", "哪", "纳", "呐");
        syl("nai", "奶", "耐", "奈");
        syl("nan", "难", "男", "南");
        syl("nang", "囊");
        syl("nao", "脑", "闹", "恼");
        syl("ne", "呢");
        syl("nei", "内", "那");
        syl("nen", "嫩");
        syl("neng", "能");
        syl("ni", "你", "泥", "逆", "拟", "尼", "腻");
        syl("nian", "年", "念", "粘");
        syl("niang", "娘", "酿");
        syl("niao", "鸟", "尿");
        syl("nie", "捏", "镍");
        syl("nin", "您");
        syl("ning", "宁", "凝");
        syl("niu", "牛", "扭", "纽");
        syl("nong", "弄", "农", "浓");
        syl("nu", "怒", "努", "奴");
        syl("nv", "女");
        syl("nuan", "暖");
        syl("nue", "虐");
        syl("nuo", "诺", "挪");
        syl("o", "哦", "噢");
        syl("ou", "欧", "偶", "呕");
        syl("pa", "怕", "爬", "趴", "帕");
        syl("pai", "拍", "排", "牌", "派");
        syl("pan", "盘", "判", "盼", "攀");
        syl("pang", "旁", "胖", "庞");
        syl("pao", "跑", "泡", "炮", "抛", "袍");
        syl("pei", "陪", "配", "赔", "佩", "培");
        syl("pen", "喷", "盆");
        syl("peng", "朋", "碰", "捧", "棚", "膨");
        syl("pi", "皮", "批", "疲", "脾", "匹", "屁", "辟");
        syl("pian", "片", "骗", "偏", "篇");
        syl("piao", "票", "漂", "飘");
        syl("pie", "撇");
        syl("pin", "拼", "品", "贫", "频");
        syl("ping", "平", "瓶", "评", "凭", "屏", "苹");
        syl("po", "破", "婆", "坡", "泼", "迫");
        syl("pou", "剖");
        syl("pu", "普", "铺", "扑", "葡", "朴");
        syl("qi", "起", "七", "期", "气", "其", "奇", "齐", "器", "妻", "弃", "企");
        syl("qia", "恰", "卡");
        syl("qian", "前", "钱", "千", "签", "浅", "欠", "牵", "迁", "谦");
        syl("qiang", "强", "抢", "墙", "枪");
        syl("qiao", "桥", "敲", "巧", "悄", "翘", "瞧");
        syl("qie", "切", "且", "窃");
        syl("qin", "亲", "琴", "勤", "侵", "秦");
        syl("qing", "请", "情", "清", "轻", "青", "晴", "庆");
        syl("qiong", "穷", "琼");
        syl("qiu", "求", "球", "秋", "丘");
        syl("qu", "去", "取", "区", "趣", "曲", "驱", "屈");
        syl("quan", "全", "权", "劝", "圈", "泉", "拳");
        syl("que", "却", "缺", "确", "雀");
        syl("qun", "群", "裙");
        syl("ran", "然", "染", "燃");
        syl("rang", "让", "嚷");
        syl("rao", "绕", "扰", "饶");
        syl("re", "热", "惹");
        syl("ren", "人", "认", "任", "忍", "仁");
        syl("reng", "仍", "扔");
        syl("ri", "日");
        syl("rong", "容", "荣", "融", "绒");
        syl("rou", "肉", "柔");
        syl("ru", "如", "入", "乳", "辱", "软");
        syl("ruan", "软");
        syl("rui", "瑞", "锐");
        syl("run", "润");
        syl("ruo", "若", "弱");
        syl("sa", "撒", "洒", "萨");
        syl("sai", "赛", "塞");
        syl("san", "三", "散", "伞");
        syl("sang", "丧", "桑", "嗓");
        syl("sao", "扫", "嫂", "骚");
        syl("se", "色", "涩");
        syl("sen", "森");
        syl("seng", "僧");
        syl("sha", "杀", "沙", "傻", "纱", "厦");
        syl("shai", "晒", "筛");
        syl("shan", "山", "善", "闪", "衫", "扇", "删");
        syl("shang", "上", "商", "伤", "尚", "赏");
        syl("shao", "少", "烧", "稍", "绍", "哨");
        syl("she", "设", "社", "舍", "蛇", "舌", "射", "涉");
        syl("shen", "什", "深", "身", "神", "伸", "审", "慎", "甚");
        syl("sheng", "生", "声", "省", "胜", "升", "剩", "盛", "圣");
        syl("shi", "是", "时", "十", "事", "实", "使", "市", "师", "识", "式", "试", "世", "室");
        syl("shou", "手", "收", "受", "首", "瘦", "寿", "兽", "售");
        syl("shu", "书", "数", "树", "熟", "属", "输", "叔", "鼠", "束", "述", "暑");
        syl("shua", "刷", "耍");
        syl("shuai", "帅", "摔", "衰", "甩");
        syl("shuan", "栓");
        syl("shuang", "双", "爽", "霜");
        syl("shui", "水", "谁", "睡", "税");
        syl("shun", "顺", "瞬");
        syl("shuo", "说", "硕");
        syl("si", "四", "思", "死", "似", "司", "丝", "私", "撕");
        syl("song", "送", "松", "宋", "诵", "耸");
        syl("sou", "搜", "嗽", "艘");
        syl("su", "诉", "速", "素", "苏", "俗", "宿", "塑");
        syl("suan", "算", "酸");
        syl("sui", "岁", "虽", "随", "碎", "睡");
        syl("sun", "孙", "损");
        syl("suo", "所", "锁", "缩", "索");
        syl("ta", "他", "她", "它", "踏", "塔", "塌");
        syl("tai", "太", "台", "态", "抬", "泰");
        syl("tan", "谈", "弹", "叹", "探", "坦", "贪", "炭");
        syl("tang", "糖", "汤", "躺", "堂", "烫", "唐");
        syl("tao", "套", "逃", "讨", "桃", "陶", "淘");
        syl("te", "特");
        syl("teng", "疼", "腾");
        syl("ti", "体", "提", "题", "替", "踢", "梯");
        syl("tian", "天", "甜", "填", "田", "添");
        syl("tiao", "条", "跳", "挑", "调");
        syl("tie", "铁", "贴");
        syl("ting", "听", "停", "厅", "挺", "庭");
        syl("tong", "同", "通", "痛", "统", "童", "铜");
        syl("tou", "头", "投", "偷", "透");
        syl("tu", "图", "土", "突", "吐", "兔", "涂", "途");
        syl("tuan", "团");
        syl("tui", "推", "退", "腿");
        syl("tun", "吞");
        syl("tuo", "脱", "拖", "托", "妥");
        syl("wa", "哇", "挖", "娃", "瓦", "袜");
        syl("wai", "外", "歪");
        syl("wan", "玩", "晚", "完", "万", "碗", "弯", "湾");
        syl("wang", "望", "忘", "王", "网", "往", "亡", "汪");
        syl("wei", "为", "位", "未", "味", "微", "围", "维", "伟", "卫", "威", "尾");
        syl("wen", "问", "文", "温", "闻", "稳", "吻");
        syl("weng", "翁");
        syl("wo", "我", "握", "卧", "窝");
        syl("wu", "五", "无", "物", "舞", "屋", "误", "午", "务", "武", "雾", "乌");
        syl("xi", "西", "喜", "洗", "吸", "戏", "细", "系", "习", "息", "席", "希", "惜");
        syl("xia", "下", "夏", "吓", "狭", "霞", "虾");
        syl("xian", "先", "现", "线", "想", "显", "险", "鲜", "闲", "县", "献", "限");
        syl("xiang", "想", "向", "像", "相", "香", "响", "乡", "享", "项", "象", "箱");
        syl("xiao", "小", "笑", "校", "消", "效", "晓", "销", "削");
        syl("xie", "写", "谢", "些", "鞋", "协", "斜", "血", "泄");
        syl("xin", "新", "心", "信", "辛", "欣", "薪");
        syl("xing", "行", "星", "姓", "兴", "性", "醒", "形", "幸", "型");
        syl("xiong", "兄", "胸", "雄", "熊");
        syl("xiu", "休", "修", "秀", "袖", "羞");
        syl("xu", "需", "许", "续", "须", "序", "虚", "徐");
        syl("xuan", "选", "宣", "旋", "悬");
        syl("xue", "学", "雪", "血", "穴");
        syl("xun", "寻", "训", "迅", "讯", "循");
        syl("ya", "呀", "压", "牙", "鸭", "亚", "押", "雅");
        syl("yan", "眼", "言", "严", "演", "研", "烟", "盐", "颜", "延", "验", "厌");
        syl("yang", "样", "养", "阳", "洋", "羊", "央", "扬", "氧");
        syl("yao", "要", "药", "摇", "咬", "腰", "邀", "耀");
        syl("ye", "也", "夜", "业", "叶", "爷", "野", "页");
        syl("yi", "一", "以", "已", "意", "医", "衣", "易", "议", "移", "依", "亿", "宜");
        syl("yin", "因", "音", "银", "引", "饮", "印", "阴", "隐");
        syl("ying", "应", "英", "影", "硬", "迎", "营", "赢", "映");
        syl("yo", "哟");
        syl("yong", "用", "永", "勇", "拥", "泳", "涌");
        syl("you", "有", "又", "由", "友", "右", "游", "油", "优", "邮", "犹");
        syl("yu", "与", "于", "语", "雨", "鱼", "育", "遇", "预", "余", "宇", "玉", "羽");
        syl("yuan", "元", "远", "圆", "原", "愿", "院", "员", "园", "源", "缘");
        syl("yue", "月", "越", "约", "乐", "阅", "跃");
        syl("yun", "云", "运", "允", "晕", "韵");
        syl("za", "杂", "砸", "扎");
        syl("zai", "在", "再", "载", "灾", "仔");
        syl("zan", "咱", "赞", "暂");
        syl("zang", "脏", "葬", "藏");
        syl("zao", "早", "造", "糟", "澡", "灶");
        syl("ze", "则", "责", "择", "泽");
        syl("zei", "贼");
        syl("zen", "怎");
        syl("zeng", "增", "赠", "曾");
        syl("zha", "炸", "扎", "渣", "眨");
        syl("zhai", "摘", "窄", "债");
        syl("zhan", "站", "战", "展", "占", "粘", "斩");
        syl("zhang", "长", "张", "章", "账", "涨", "掌", "丈");
        syl("zhao", "找", "照", "着", "招", "朝", "赵");
        syl("zhe", "这", "着", "者", "折", "哲", "遮");
        syl("zhen", "真", "正", "针", "震", "阵", "珍", "镇", "诊");
        syl("zheng", "正", "整", "证", "争", "政", "挣", "征");
        syl("zhi", "只", "知", "之", "直", "指", "纸", "支", "制", "治", "值", "职", "志");
        syl("zhong", "中", "种", "重", "众", "终", "钟", "忠");
        syl("zhou", "周", "州", "洲", "舟", "粥", "轴");
        syl("zhu", "主", "住", "注", "助", "祝", "猪", "竹", "著", "柱", "煮");
        syl("zhua", "抓");
        syl("zhuai", "拽");
        syl("zhuan", "转", "专", "赚", "砖");
        syl("zhuang", "装", "壮", "状", "撞");
        syl("zhui", "追", "坠");
        syl("zhun", "准");
        syl("zhuo", "桌", "捉", "着", "拙");
        syl("zi", "字", "自", "子", "资", "紫", "仔", "姿");
        syl("zong", "总", "从", "宗", "纵", "综");
        syl("zou", "走", "奏");
        syl("zu", "组", "足", "租", "族", "祖", "阻");
        syl("zuan", "钻");
        syl("zui", "最", "嘴", "醉", "罪");
        syl("zun", "尊", "遵");
        syl("zuo", "做", "作", "坐", "左", "座", "昨");
    }
}
