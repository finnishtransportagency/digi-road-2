
CREATE INDEX asset_floating_idx ON asset (floating);
CREATE INDEX asset_geometry_sx ON asset USING gist(geometry);
CREATE INDEX asset_type_and_modified_by_idx ON asset (asset_type_id, modified_by);
CREATE INDEX asset_type_idx ON asset (asset_type_id);
CREATE UNIQUE INDEX external_id_index ON asset (external_id);
CREATE INDEX municipality_code_idx ON asset (municipality_code);
CREATE INDEX type_modified_by_and_date_idx ON asset (modified_date, asset_type_id, modified_by);
CREATE INDEX hist_asset_floating_idx ON asset_history (floating);
CREATE INDEX hist_asset_type_idx ON asset_history (asset_type_id);
CREATE INDEX hist_a_type_and_modi_by_idx ON asset_history (asset_type_id, modified_by);
CREATE UNIQUE INDEX hist_external_id_index ON asset_history (external_id);
CREATE INDEX hist_municipality_code_idx ON asset_history (municipality_code);
CREATE INDEX hist_type_modi_by_and_date_idx ON asset_history (modified_date, asset_type_id, modified_by);
CREATE INDEX asset_id_idx ON asset_link (asset_id);
CREATE INDEX position_id_idx ON asset_link (position_id);
CREATE INDEX hist_asset_id_idx ON asset_link_history (asset_id);
CREATE INDEX hist_position_id_idx ON asset_link_history (position_id);
CREATE INDEX enumvalue_property_id_idx ON enumerated_value (property_id);
CREATE INDEX export_report_created_by_idx ON export_report (created_by);
CREATE UNIQUE INDEX funct_class_idx ON functional_class (link_id);
CREATE INDEX created_by_idx ON import_log (created_by);
CREATE INDEX inaccurate_asset_all_info_idx ON inaccurate_asset (asset_type_id, municipality_code, administrative_class);
CREATE INDEX inaccurate_asset_id_idx ON inaccurate_asset (asset_id);
CREATE INDEX inaccurate_asset_type_id_idx ON inaccurate_asset (asset_type_id);
CREATE UNIQUE INDEX incomp_linkid_idx ON incomplete_link (link_id);
CREATE INDEX lane_code_index ON lane (lane_code);
CREATE INDEX lane_municipality_code_idx ON lane (municipality_code);
CREATE INDEX lane_valid_to_idx ON lane (valid_to);
CREATE INDEX lane_history_code_index ON lane_history (lane_code);
CREATE INDEX lane_history_mun_code_idx ON lane_history (municipality_code);
CREATE INDEX lane_history_new_id_idx ON lane_history (new_id);
CREATE INDEX lane_history_old_id_idx ON lane_history (old_id);
CREATE INDEX lane_history_valid_to_idx ON lane_history (valid_to);
CREATE INDEX lane_history_pos_link_id_idx ON lane_history_position (link_id);
CREATE INDEX lane_hist_pos_linkid_sidec_idx ON lane_history_position (link_id, side_code);
CREATE INDEX lane_position_link_id_idx ON lane_position (link_id);
CREATE INDEX lane_pos_linkid_side_code_idx ON lane_position (link_id, side_code);
CREATE UNIQUE INDEX link_type_idx ON link_type (link_id);
CREATE INDEX lrm_position_link_id_idx ON lrm_position (link_id);
CREATE INDEX lrm_position_mml_idx ON lrm_position (mml_id);
CREATE INDEX hist_lrm_position_link_id_idx ON lrm_position_history (link_id);
CREATE INDEX hist_lrm_position_mml_idx ON lrm_position_history (mml_id);
CREATE INDEX manoeuvre_valid_to_idx ON manoeuvre (valid_to);
CREATE INDEX element_manoeuvre_idx ON manoeuvre_element (manoeuvre_id);
CREATE INDEX element_source_link_idx ON manoeuvre_element (link_id, element_type);
CREATE UNIQUE INDEX uniq_first_element ON manoeuvre_element ((case element_type when 1 then manoeuvre_id else null end));
CREATE UNIQUE INDEX uniq_last_element ON manoeuvre_element ((case element_type when 3 then manoeuvre_id else null end));
CREATE INDEX hist_element_manoeuvre_idx ON manoeuvre_element_history (manoeuvre_id);
CREATE INDEX hist_element_source_link_idx ON manoeuvre_element_history (link_id, element_type);
CREATE INDEX manoeuvre_exceptions_mid ON manoeuvre_exceptions (manoeuvre_id);
CREATE INDEX hist_manoeuvre_exceptions_mid ON manoeuvre_exceptions_history (manoeuvre_id);
CREATE INDEX hist_manoeuvre_valid_to_idx ON manoeuvre_history (valid_to);
CREATE INDEX m_valid_period_mid ON manoeuvre_validity_period (manoeuvre_id);
CREATE INDEX hist_m_valid_period_mid ON manoeuvre_val_period_history (manoeuvre_id);
CREATE INDEX multiple_choice_value_sx ON multiple_choice_value (asset_id, property_id);
CREATE INDEX hist_multiple_choice_value_sx ON multiple_choice_value_history (asset_id, property_id);
CREATE INDEX municipality_assettype_idx ON municipality_verification (municipality_id, asset_type_id);
CREATE INDEX municipality_idx ON municipality_verification (municipality_id);
CREATE INDEX numpropval_asset_prop_id_idx ON number_property_value (asset_id, property_id);
CREATE INDEX numpropval_prop_id_idx ON number_property_value (property_id);
CREATE INDEX hist_npv_asset_prop_id_idx ON number_property_value_history (asset_id, property_id);
CREATE INDEX hist_numpropval_prop_id_idx ON number_property_value_history (property_id);
CREATE INDEX proh_except_idx ON prohibition_exception (prohibition_value_id);
CREATE INDEX hist_proh_except_idx ON prohibition_exception_history (prohibition_value_id);
CREATE INDEX proh_valid_period_idx ON prohibition_validity_period (prohibition_value_id);
CREATE INDEX proh_value_asset_idx ON prohibition_value (asset_id);
CREATE INDEX hist_proh_value_asset_idx ON prohibition_value_history (asset_id);
CREATE INDEX hist_proh_val_period_idx ON proh_val_period_history (prohibition_value_id);
CREATE INDEX property_assettype_id_idx ON property (asset_type_id);
CREATE INDEX link_id_temp_address ON temp_road_address_info (link_id);
CREATE INDEX municipality_temp_address ON temp_road_address_info (municipality_code);
CREATE INDEX bus_stop_asset_id_idx ON terminal_bus_stop_link (bus_stop_asset_id);
CREATE INDEX terminal_asset_id_idx ON terminal_bus_stop_link (terminal_asset_id);
CREATE UNIQUE INDEX aid_pid_text_property_sx ON text_property_value (asset_id, property_id, grouped_id);
CREATE UNIQUE INDEX hist_aid_pid_text_property_sx ON text_property_value_history (asset_id, property_id, grouped_id);
CREATE UNIQUE INDEX traffic_dir_link_idx ON traffic_direction (link_id);
CREATE INDEX unknown_speed_limit_mc_ac ON unknown_speed_limit (municipality_code, administrative_class);