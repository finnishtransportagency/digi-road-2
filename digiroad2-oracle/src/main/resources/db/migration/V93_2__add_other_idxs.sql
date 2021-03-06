DELETE FROM LINK_TYPE t1 WHERE EXISTS (SELECT 1 FROM LINK_TYPE t2 WHERE t2.id < t1.id AND t2.link_id = t1.link_id);
DELETE FROM TRAFFIC_DIRECTION t1 WHERE EXISTS (SELECT 1 FROM TRAFFIC_DIRECTION t2 WHERE t2.id < t1.id AND t2.link_id = t1.link_id);
DELETE FROM FUNCTIONAL_CLASS t1 WHERE EXISTS (SELECT 1 FROM FUNCTIONAL_CLASS t2 WHERE t2.id < t1.id AND t2.link_id = t1.link_id);
-- DELETE duplicates, id 38266 found in all env

CREATE UNIQUE INDEX TRAFFIC_DIR_LINK_IDX ON TRAFFIC_DIRECTION(LINK_ID);
CREATE UNIQUE INDEX LINK_TYPE_IDX ON LINK_TYPE(LINK_ID);
CREATE UNIQUE INDEX FUNCT_CLASS_IDX ON FUNCTIONAL_CLASS(LINK_ID);

CREATE INDEX PROH_VALUE_ASSET_IDX ON PROHIBITION_VALUE(ASSET_ID);
CREATE INDEX PROH_VALID_PERIOD_IDX ON PROHIBITION_VALIDITY_PERIOD(PROHIBITION_VALUE_ID);
CREATE INDEX PROH_EXCEPT_IDX ON PROHIBITION_EXCEPTION(PROHIBITION_VALUE_ID);

