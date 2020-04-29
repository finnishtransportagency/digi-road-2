(function(root) {
  root.TrafficSignForm = function() {
    PointAssetForm.call(this);
    var me = this;
    var defaultAdditionalPanelValue = null;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.feedbackCollection = parameters.feedbackCollection;
      defaultAdditionalPanelValue = _.find(parameters.pointAsset.newAsset.propertyData, function(obj){return obj.publicId === 'additional_panel';}).defaultValue;
      me.bindEvents(parameters);
      me.selectedAsset = parameters.pointAsset.selectedPointAsset;
    };

    var propertyOrdering = [
      'trafficSigns_type',
      'trafficSigns_value',
      'trafficSigns_info',
      'municipality_id',
      'main_sign_text',
      'structure',
      'condition',
      'size',
      'height',
      'coating_type',
      'sign_material',
      'location_specifier',
      'terrain_coordinates_x',
      'terrain_coordinates_y',
      'lane',
      'lane_type',
      'life_cycle',
      'trafficSign_start_date',
      'trafficSign_end_date',
      'type_of_damage',
      'urgency_of_repair',
      'lifespan_left',
      'suggest_box',
      'old_traffic_code',
      'counter'
    ];

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var allTrafficSignProperties = asset.propertyData;
      var trafficSignSortedProperties = me.sortAndFilterProperties(allTrafficSignProperties, propertyOrdering);

      var components = _.reduce(_.map(trafficSignSortedProperties, function (feature) {
        feature.localizedName = window.localizedStrings[feature.publicId];
        var propertyType = feature.propertyType;

        switch (propertyType) {
          case "number":
          case "text": return me.textHandler(feature);
          case "single_choice": return feature.publicId === 'trafficSigns_type' ? singleChoiceTrafficSignTypeHandler(feature, collection, asset) : me.singleChoiceHandler(feature);
          case "read_only_number": return me.readOnlyHandler(feature);
          case "date": return me.dateHandler(feature);
          case "checkbox": return feature.publicId === 'suggest_box' ? me.suggestedBoxHandler (feature, authorizationPolicy) : me.checkboxHandler(feature);
        }

      }), function(prev, curr) { return prev + curr; }, '');

      var additionalPanels = me.getProperties(asset.propertyData, "additional_panel");
      var checked = _.isEmpty(additionalPanels.values) ? '' : 'checked';
      var renderedPanels = checked ? renderAdditionalPanels(additionalPanels, collection, asset) : '';

      function getSidePlacement() {
        return _.head(me.getProperties(asset.propertyData, "opposite_side_sign").values);
      }

      var panelCheckbox =
        '    <div class="form-group editable edit-only form-traffic-sign-panel additional-panel-checkbox">' +
        '      <div class="checkbox" >' +
        '        <input id="additional-panel-checkbox" type="checkbox" ' + checked + '>' +
        '      </div>' +
        '        <label class="traffic-panel-checkbox-label">Linkitä lisäkilpiä</label>' +
        '    </div>';

      var wrongSideInfo = asset.id !== 0 && !_.isEmpty(getSidePlacement().propertyValue) ?
        '    <div id="wrongSideInfo" class="form-group read-only">' +
        '        <label class="control-label">' + 'Liikenteenvastainen' + '</label>' +
        '        <p class="form-control-static">' + getSidePlacement().propertyDisplayValue + '</p>' +
        '    </div>' : '';

      return components + wrongSideInfo + panelCheckbox + renderedPanels;
    };

    this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
      var id = selectedAsset.getId();

      var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
      var header = '<span>' + title + '</span>';
      var form = me.renderAssetFormElements(selectedAsset, localizedTexts, collection, authorizationPolicy);
      var footer = me.renderButtons();

      rootElement.find("#feature-attributes-header").html(header);
      rootElement.find("#feature-attributes-form").html(form);
      rootElement.find(".suggestion-box").before(me.renderValidityDirection(selectedAsset));
      dateutil.addTwoDependentDatePickers($('#trafficSign_start_date'),  $('#trafficSign_end_date'));
      if(me.pointAsset.lanePreview)
        rootElement.find("#feature-attributes-form").prepend(me.renderPreview(roadCollection, selectedAsset));
      rootElement.find("#feature-attributes-footer").html(footer);

      me.addingPreBoxEventListeners(rootElement, selectedAsset, id);

      this.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
      rootElement.find('.form-point-asset').on('change', function() {
        selectedAsset.setPropertyByPublicId('opposite_side_sign', '0');  // force the field to be filled
      });

      rootElement.find('.form-point-asset input[type=text],select').on('change datechange', function (event) {
        var eventTarget = $(event.currentTarget);
        var propertyPublicId = eventTarget.attr('id');
        selectedAsset.setPropertyByPublicId(propertyPublicId, eventTarget.val());
      });

      rootElement.find('.form-point-asset select#trafficSigns_type').on('change', function (event) {
        $('.form-point-asset select#trafficSigns_type').html(singleChoiceSubType(collection, $(event.currentTarget).val()));
        selectedAsset.setPropertyByPublicId('trafficSigns_type', $('.form-point-asset select#trafficSigns_type').val());
      });

      rootElement.find('#additional-panel-checkbox').on('change', function (event) {
        if(!$(event.currentTarget).prop('checked')) {
          $('.panel-group-container').remove();
          selectedAsset.setAdditionalPanels();
        }
        else {
          $('.additional-panel-checkbox').after(renderAdditionalPanels({values:[defaultAdditionalPanelValue]}, collection, selectedAsset.get()));
          setAllPanels();
        }
        bindPanelEvents();
      });

      bindPanelEvents();

      function toggleButtonVisibility() {
        var cont = rootElement.find('.panel-group-container');
        var panels = cont.children().size();

        cont.find('.remove-panel').toggle(panels !== 1);
        cont.find('.add-panel').prop("disabled", panels === 3);
      }

      rootElement.find('button#change-validity-direction').on('click', function() {
        var previousValidityDirection = selectedAsset.get().validityDirection;
        selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
        if(me.pointAsset.lanePreview)
          $('.preview-div').replaceWith(me.renderPreview(roadCollection, selectedAsset));
      });

      function bindPanelEvents(){
        rootElement.find('.remove-panel').on('click', function (event) {
          removeSingle(event);
          $('.panel-group-container').replaceWith(renderAdditionalPanels({values:setAllPanels()}, collection));
          bindPanelEvents();
        });

        rootElement.find('input[type=text]#panelValue, input[type=text]#panelInfo, input[type=text]#text, .form-traffic-sign-panel select').on('change input', function (event) {
          setSinglePanel(event);
        });

        rootElement.find('.add-panel').on('click', function (event) {
          $(event.currentTarget).parent().after(renderAdditionalPanels({values:[defaultAdditionalPanelValue]}, collection));
          $('.panel-group-container').replaceWith(renderAdditionalPanels({values:setAllPanels()}, collection));
          bindPanelEvents();
        });

        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly());

        toggleButtonVisibility();
      }

      var setAllPanels = function() {
          var allPanels = $('.single-panel-container').map(function(index){
            var self = this;
            return {
              formPosition: (index + 1),
              panelType: parseInt($(self).find('#panelType').val()),
              panelValue: $(self).find('#panelValue').val(),
              panelInfo:  $(self).find('#panelInfo').val(),
              text:  $(self).find('#text').val(),
              size:  parseInt($(self).find('#size').val()),
              coating_type:  parseInt($(self).find('#coating_type').val()),
              additional_panel_color:  parseInt($(self).find('#additional_panel_color').val())
            };
          });
          selectedAsset.setAdditionalPanels(allPanels.toArray());
          return allPanels;
      };

      var setSinglePanel = function(event) {
        var eventTarget = $(event.currentTarget);
        var panelId = eventTarget.parent().parent().attr('id');
        var container = $('.single-panel-container#'+panelId);
        var panel = {
          formPosition: parseInt(panelId),
          panelType: parseInt(container.find('#panelType').val()),
          panelValue: container.find('#panelValue').val(),
          panelInfo:  container.find('#panelInfo').val(),
          text:  container.find('#text').val(),
          size:  parseInt(container.find('#size').val()),
          coating_type:  parseInt(container.find('#coating_type').val()),
          additional_panel_color:  parseInt(container.find('#additional_panel_color').val())
        };
        selectedAsset.setAdditionalPanel(panel);
      };

      var removeSingle = function(event) {
        $('.single-panel-container#'+ $(event.currentTarget).prop('id')).remove();
      };
    };

    var singleChoiceSubType = function (collection, mainType, property, asset) {
      var propertyValue = (_.isUndefined(property) || property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var propertyDisplayValue = (_.isUndefined(property) || property.values.length === 0) ? '' : _.head(property.values).propertyDisplayValue;
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function (enumerated) {
        return enumerated.publicId == 'trafficSigns_type';
      }), function (val) {
        return val.values;
      });
      var groups = collection.getGroup(signTypes);
      var subTypesTrafficSigns;

      if (isPedestrianOrCyclingRoadLink(asset)) {
        subTypesTrafficSigns = _.map(_.map(groups)[mainType], function (group) {
          if ( ['70','71','72'].indexOf( group.propertyValue) >= 0 ) {
            return $('<option>',
                {
                  value: group.propertyValue,
                  selected: propertyValue == group.propertyValue,
                  text: group.propertyDisplayValue
                }
            )[0].outerHTML;
          }
        }).join('');
      } else {
        subTypesTrafficSigns = _.map(_.map(groups)[mainType], function (group) {
          return $('<option>',
            {
              value: group.propertyValue,
              selected: propertyValue == group.propertyValue,
              text: group.propertyDisplayValue
            }
          )[0].outerHTML;
        }).join('');
      }

      return '<div class="form-group editable form-point-asset">' +
        '      <label class="control-label"> ALITYYPPI</label>' +
        '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id="trafficSigns_type">  ' +
        subTypesTrafficSigns +
        '      </select></div>';
    };

    function isPedestrianOrCyclingRoadLink(asset) {
      if (_.isUndefined(asset)){
        return false;
      }

      var roadLink = me.roadCollection.getRoadLinkByLinkId(asset.linkId);
      var roadLinkData;

      if (roadLink){
        roadLinkData = roadLink.getData();
        if (!_.isUndefined(roadLinkData) && roadLinkData.linkType === 8) {
          return true;
        }
      }
      return false;
    }

    var singleChoiceTrafficSignTypeHandler = function (property, collection, asset) {
      var propertyValue = (property.values.length === 0) ? '' : _.head(property.values).propertyValue;
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, { 'publicId': property.publicId}), function(val) {return val.values; });
      var auxProperty = property;
      var groups =  collection.getGroup(signTypes);
      var groupKeys = Object.keys(groups);
      var mainTypeDefaultValue = _.indexOf(_.map(groups, function (group) {return _.some(group, function(val) {return val.propertyValue == propertyValue;});}), true);

      if (isPedestrianOrCyclingRoadLink(asset)) {
        var mandatorySignsDefaultValue = "70";
        /* get the correct index for group to be used in subSingleChoice */
        mainTypeDefaultValue = _.indexOf(_.map(groups, function (group) {return _.some(group, function(val) {return val.propertyValue == mandatorySignsDefaultValue;});}), true);

        /* Only after get the correct index of the group (mainTypeDefaultValue) I can reset the values for the */
        /* correct one to be used in the 'main singleChoice field' */
        signTypes = _.map(signTypes,function(sign) { return _.filter(sign, function(x) { if ( x.propertyValue == '70') return x; });  });
        groups =  collection.getGroup(signTypes);
        groupKeys = Object.keys(groups);

        /* Case asset is new...we will ignore the property value from parameter to load the subSingleChoice */
        if (asset.id === 0) {
          auxProperty  = undefined;
        }
      }

      var counter = 0;
      var mainTypesTrafficSigns = _.map(groupKeys, function (label) {
        return $('<option>',
          { selected: counter === mainTypeDefaultValue,
            value: counter++,
            text: label}
        )[0].outerHTML; }).join('');

      return '' +
        '    <div class="form-group editable form-point-asset">' +
        '      <label class="control-label">' + property.localizedName + '</label>' +
        '      <p class="form-control-static">' + (groupKeys[mainTypeDefaultValue] || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id=' + property.publicId +'>' +
        mainTypesTrafficSigns +
        '      </select>' +
        '    </div>' +
        singleChoiceSubType( collection, mainTypeDefaultValue, auxProperty, asset );
    };

    var sortPanelKeys = function(properties) {
      var propertyOrdering = [
        'formPosition',
        'panelType',
        'panelValue',
        'panelInfo',
        'text',
        'size',
        'coating_type',
        'additional_panel_color',
      ];

      var sorted = {};

      _.forEach(propertyOrdering, function(key){
        sorted[key] = properties[key];
      });
      return sorted;
    };

    var renderAdditionalPanels = function (property, collection, asset) {
      var sortedByProperties = _.map(property.values, function(prop) {
        return sortPanelKeys(prop);
      });

      var sortedByFormPosition = _.sortBy(sortedByProperties, function(o){
        return o.formPosition;
      });

      var panelContainer = $('<div class="panel-group-container"></div>');

      var components = _.flatMap(sortedByFormPosition, function(panel, index) {
        var body =
          $('<div class="single-panel-container" id='+ (index + 1)+'>' +
          Object.entries(panel).map(function (feature) {

            switch (_.head(feature)) {
              case "formPosition": return panelLabel(index+1);
              case "panelValue":
              case "panelInfo":
              case "text": return panelTextHandler(feature);
              case "panelType": return singleChoiceForPanelTypes(feature, collection);
              case "size":
              case "coating_type":
              case "additional_panel_color": return singleChoiceForPanels(feature);
            }

          }).join(''));

        var buttonDiv = $('<div class="form-group editable form-traffic-sign-panel traffic-panel-buttons">' + (sortedByFormPosition.length === 1 ? '' : removeButton(index+1)) + addButton(index+1) + '</div>');

        body.filter('.single-panel-container').append(buttonDiv);

        return body;
      });
      return panelContainer.append(components)[0].outerHTML;
    };

    var removeButton = function(id) {
      return '<button class="btn edit-only btn-secondary remove-panel" id="'+id+'" >Poista lisäkilpi</button>';
    };

    var addButton = function(id) {
      return '<button class="btn edit-only editable btn-secondary add-panel" id="'+id+'" >Uusi lisäkilpi</button>';
    };

    var singleChoiceForPanelTypes = function (property, collection, asset) {
      var propertyValue = _.isUndefined(_.last(property))  ? '' : _.last(property);
      var signTypes = _.map(_.filter(me.enumeratedPropertyValues, function(enumerated) { return enumerated.publicId == 'trafficSigns_type' ; }), function(val) {return val.values; });
      var panels = _.find(collection.getAdditionalPanels(signTypes));
      var propertyDisplayValue;

      if (isPedestrianOrCyclingRoadLink(asset)) {
        panels = _.filter(panels, function(p) { if (p.propertyValue == '61') return p;} );
        propertyDisplayValue = _.isUndefined(panels) && panels.length === 0 ? "" : panels[0].propertyDisplayValue;
      }
      else {
        propertyDisplayValue = _.find(panels, function(panel){return panel.propertyValue == propertyValue.toString();}).propertyDisplayValue;
      }

      var subTypesTrafficSigns = _.map(_.map(panels, function (group) {
        return $('<option>',
          {
            value: group.propertyValue,
            selected: propertyValue == group.propertyValue,
            text: group.propertyDisplayValue
          }
        )[0].outerHTML;
      })).join('');

      return '<div class="form-group editable form-traffic-sign-panel">' +
        '      <label class="control-label"> ALITYYPPI</label>' +
        '      <p class="form-control-static">' + (propertyDisplayValue || '-') + '</p>' +
        '      <select class="form-control" style="display:none" id="panelType">  ' +
        subTypesTrafficSigns +
        '      </select></div>';
    };

    //can't be associated with traffic signs
    var additionalPanelColorSettings = [
      { propertyValue: "1", propertyDisplayValue: "Sininen", checked: false },
      { propertyValue: "2", propertyDisplayValue: "Keltainen", checked: false },
      { propertyValue: "999", propertyDisplayValue: "Ei tietoa", checked: false }
    ];

    var singleChoiceForPanels = function (property) {
      var publicId = _.head(property);
      var propertyValue = (!_.isEmpty(_.last(property))) ? '' : _.last(property);
      var propertyValues = publicId === "additional_panel_color" ? additionalPanelColorSettings : _.head(_.map(_.filter(me.enumeratedPropertyValues, { 'publicId': publicId }), function(val) {return val.values; }));
      var propertyDefaultValue = _.indexOf(_.map(propertyValues, function (prop) {return _.some(prop, function(propValue) {return propValue == propertyValue;});}), true);
      var selectableValues = _.map(propertyValues, function (label) {
        return $('<option>',
            { selected: propertyValue == label.propertyValue,
              value: parseInt(label.propertyValue),
              text: label.propertyDisplayValue}
        )[0].outerHTML; }).join('');

      switch (publicId) {
        case "size": property.label = "KOKO"; break;
        case "coating_type": property.label = "KALVON TYYPPI"; break;
        case "additional_panel_color": property.label = "LISÄKILVEN VÄRI"; break;
      }

      return '' +
          '    <div class="form-group editable form-traffic-sign-panel">' +
          '      <label class="control-label">' + property.label + '</label>' +
          '      <p class="form-control-static">' + (propertyValues[propertyDefaultValue].propertyDisplayValue || '-') + '</p>' +
          '      <select class="form-control" style="display:none" id="' + publicId +'">' +
          selectableValues +
          '      </select>' +
          '    </div>';
    };

    var panelTextHandler = function (property) {
      var publicId = _.first(property);
      var propertyValue = _.isUndefined(_.last(property)) ? '' : _.last(property);

      switch (publicId) {
        case "panelValue": property.label = "ARVO"; break;
        case "panelInfo": property.label = "LISÄTIETO"; break;
        case "text": property.label = "TEKSTI"; break;
      }

      return '' +
        '    <div class="form-group editable form-traffic-sign-panel">' +
        '        <label class="control-label">' + property.label + '</label>' +
        '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
        '        <input type="text" class="form-control" id="' + publicId + '" value="' + propertyValue + '">' +
        '    </div>';
    };

    var panelLabel = function(index) {
      return '' +
        '    <div class="form-group editable form-traffic-sign-panel">' +
        '        <label class="traffic-panel-label">Lisäkilpi ' + index + '</label>' +
        '    </div>';
    };
  };
})(this);