CREATE TABLE demo (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  name       VARCHAR(128) NOT NULL,
  status     TINYINT      NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 逻辑删除必须把 deleted 并入唯一索引，否则软删后同名记录插不进来。
  -- 注意：这只能容纳一条同名软删记录，第二次软删同名会撞唯一键；
  -- 需要多次软删时，改为删除时把 deleted 写成时间戳/主键。
  UNIQUE KEY uk_name_deleted (name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
