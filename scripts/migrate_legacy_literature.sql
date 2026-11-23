-- 存量数据治理：把 user_id 为空的历史文献归属到指定用户。
--
-- 背景：user_id 列引入前的文献没有属主，当前代码对这些"无主文献"做了
-- 兼容放行（任何登录用户可删除/重解析）。运行本脚本归属后即可消除该风险。
--
-- 用法（先 dry 查看再执行）：
--   1) SELECT id, title, created_at FROM literature WHERE user_id IS NULL;   -- 预览
--   2) 将下方 @OWNER_EMAIL 改为接收这些文献的账号邮箱（建议管理员/你自己）
--   3) 执行 UPDATE；确认 literature 无主记录为 0 后，可按 README 提示移除代码兼容分支
--
-- 若无主文献已无保留价值，也可改为直接删除（先备份）：
--   DELETE FROM user_literature_state WHERE literature_id IN (SELECT id FROM literature WHERE user_id IS NULL);
--   DELETE FROM literature WHERE user_id IS NULL;

USE example_db;

SET @OWNER_EMAIL = 'your-admin@example.com';
SET @OWNER_ID = (SELECT id FROM users WHERE email = @OWNER_EMAIL LIMIT 1);

-- 防呆：目标用户不存在时中止（受 MySQL 无断言语句限制，用报错表达式实现）
SELECT CASE WHEN @OWNER_ID IS NULL THEN
    CAST('目标用户不存在：请先检查 @OWNER_EMAIL' AS SIGNED)
ELSE 0 END AS precheck;

UPDATE literature SET user_id = @OWNER_ID WHERE user_id IS NULL;

SELECT COUNT(*) AS remaining_orphans FROM literature WHERE user_id IS NULL;
