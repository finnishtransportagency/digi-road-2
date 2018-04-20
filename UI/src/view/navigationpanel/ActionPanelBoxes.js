(function(ActionPanelBoxes) {
  var selectToolIcon = '<img src="images/select-tool.svg"/>';
  var cutToolIcon = '<img src="images/cut-tool.svg"/>';
  var addToolIcon = '<img src="images/add-tool.svg"/>';
  var rectangleToolIcon = '<img src="images/rectangle-tool.svg"/>';
  var polygonToolIcon = '<img src="images/polygon-tool.svg"/>';
  var terminalToolIcon = '<img src="images/add-terminal-tool.svg"/>';
  var checkIcon = '<img src="images/check-icon.png" title="Kuntakäyttäjän todentama"/>';

  var Tool = function(toolName, icon, selectedAssetModel) {
    var className = toolName.toLowerCase();
    var element = $('<div class="action"/>').addClass(className).attr('action', toolName).append(icon).click(function() {
      executeOrShowConfirmDialog(function() {
        applicationModel.setSelectedTool(toolName);
      });
    });
    var deactivate = function() {
      element.removeClass('active');
    };
    var activate = function() {
      element.addClass('active');
    };

    return {
      element: element,
      deactivate: deactivate,
      activate: activate,
      name: toolName
    };
  };

  var ToolSelection = function(tools) {
    var element = $('<div class="panel-section panel-actions" />');
    _.each(tools, function(tool) {
      element.append(tool.element);
    });
    var hide = function() {
      element.hide();
    };
    var show = function() {
      element.show();
    };
    var deactivateAll = function() {
      _.each(tools, function(tool) {
        tool.deactivate();
      });
    };
    var reset = function() {
      deactivateAll();
      tools[0].activate();
    };
    eventbus.on('tool:changed', function(name) {
      _.each(tools, function(tool) {
        if (tool.name != name) {
          tool.deactivate();
        } else {
          tool.activate();
        }
      });
    });

    hide();

    return {
      element: element,
      reset: reset,
      show: show,
      hide: hide
    };
  };

  ActionPanelBoxes.selectToolIcon = selectToolIcon;
  ActionPanelBoxes.cutToolIcon = cutToolIcon;
  ActionPanelBoxes.addToolIcon = addToolIcon;
  ActionPanelBoxes.Tool = Tool;
  ActionPanelBoxes.ToolSelection = ToolSelection;
  ActionPanelBoxes.rectangleToolIcon = rectangleToolIcon;
  ActionPanelBoxes.polygonToolIcon = polygonToolIcon;
  ActionPanelBoxes.terminalToolIcon = terminalToolIcon;

  ActionPanelBoxes.SpeedLimitBox = function(selectedSpeedLimit) {
    var speedLimits = [120, 100, 90, 80, 70, 60, 50, 40, 30, 20];
    var speedLimitLegendTemplate = _.map(speedLimits, function(speedLimit) {
      return '<div class="legend-entry">' +
               '<div class="label">' + speedLimit + '</div>' +
               '<div class="symbol linear speed-limit-' + speedLimit + '" />' +
             '</div>';
    }).join('');
    var speedLimitHistoryCheckBox = [
      '<div class="check-box-container">',
          '<input id="historyCheckbox" type="checkbox" /> <lable>Näytä poistuneet tielinkit</lable>' +
    '</div>'].join('');

    var speedLimitComplementaryCheckBox = [
      '<div class="check-box-container">' +
        '<input id="compCheckbox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
      '</div>'
    ].join('');

    var speedLimitSignsCheckBox = [
      '<div class="check-box-container">' +
      '<input id="signsCheckbox" type="checkbox" /> <lable>Näytä liikennemerkit</lable>' +
      '</div>' +
      '</div>'
    ].join('');

    var header = ['<div id="left-panel">    Nopeusrajoitukset</div>' +
        ' <div id="right-panel">' + checkIcon + '</div>'].join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      header,
      '  </header>',
      '  <div class="panel-section panel-legend linear-asset-legend speed-limit-legend">',
            speedLimitLegendTemplate,
            speedLimitHistoryCheckBox,
            speedLimitComplementaryCheckBox,
            speedLimitSignsCheckBox,
      '  </div>',
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var toolSelection = new ToolSelection([
      new Tool('Select', selectToolIcon, selectedSpeedLimit),
      new Tool('Cut', cutToolIcon, selectedSpeedLimit)
    ]);
    var editModeToggle = new EditModeToggleButton(toolSelection);
    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
          elements.expanded.append(editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindExternalEventHandlers();

    var element = $('<div class="panel-group speed-limits"/>')
      .append(elements.expanded)
      .hide();

    function show() {
      if (editModeToggle.hasNoRolesPermission(userRoles)) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    elements.expanded.find('#historyCheckbox').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      if (eventTarget.prop('checked')) {
        eventbus.trigger('speedLimits:showSpeedLimitsHistory');
      } else {
        eventbus.trigger('speedLimits:hideSpeedLimitsHistory');
      }
    });

    elements.expanded.find('#compCheckbox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('speedLimits:showSpeedLimitsComplementary');
      } else {
        if (applicationModel.isDirty()) {
          $(event.currentTarget).prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('speedLimits:hideSpeedLimitsComplementary');
        }
      }
    });

    elements.expanded.find('#signsCheckbox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('speedLimit:showReadOnlyTrafficSigns');
      } else {
        eventbus.trigger('speedLimit:hideReadOnlyTrafficSigns');
      }
    });

    eventbus.on('verificationInfo:fetched', function(visible) {
      var img = elements.expanded.find('#right-panel');
      if (visible)
        img.css('display','inline');
      else
        img.css('display','none');
    });

    return {
      title: 'Nopeusrajoitus',
      layerName: 'speedLimit',
      element: element,
      show: show,
      hide: hide
    };
  };

  ActionPanelBoxes.WinterSpeedLimitBox = function(asset) {
    var speedLimits = [100, 80, 70, 60];
    var speedLimitLegendTemplate = _.map(speedLimits, function(speedLimit) {
      return '<div class="legend-entry">' +
        '<div class="label">' + speedLimit + '</div>' +
        '<div class="symbol linear speed-limit-' + speedLimit + '" />' +
        '</div>';
    }).join('');

    var complementaryLinkCheckBox = asset.allowComplementaryLinks ? [
        '<div class="check-box-container">' +
        '<input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
        '</div>' +
        '</div>'
      ].join('') : '';


    var expandedTemplate = [
      '<div class="panel ' + asset.layerName +'">',
      '  <header class="panel-header expanded">',
      '    ' + asset.title + (asset.editControlLabels.showUnit ? ' ('+asset.unit+')': ''),
      '  </header>',
      '  <div class="panel-section panel-legend linear-asset-legend speed-limit-legend">',
      speedLimitLegendTemplate,
      complementaryLinkCheckBox,
      '  </div>',
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var toolSelection = new ToolSelection([
      new Tool('Select', selectToolIcon, asset.selectedLinearAsset),
      new Tool('Cut', cutToolIcon, asset.selectedLinearAsset),
      new Tool('Rectangle', rectangleToolIcon, asset.selectedLinearAsset),
      new Tool('Polygon', polygonToolIcon, asset.selectedLinearAsset)
    ]);
    var editModeToggle = new EditModeToggleButton(toolSelection);
    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
          elements.expanded.append(editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindExternalEventHandlers();

    var element = $('<div class="panel-group winter-speed-limits"/>')
      .append(elements.expanded)
      .hide();

    function show() {
      if (editModeToggle.hasNoRolesPermission(userRoles)) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    elements.expanded.find('#complementaryLinkCheckBox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('complementaryLinks:show');
      } else {
        if (applicationModel.isDirty()) {
          $(event.currentTarget).prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('complementaryLinks:hide');
        }
      }
    });

    return {
      title: asset.title,
      layerName: asset.layerName,
      element: element,
      show: show,
      hide: hide
    };
  };

  var executeOrShowConfirmDialog = function(f) {
    if (applicationModel.isDirty()) {
      new Confirm();
    } else {
      f();
    }
  };

  ActionPanelBoxes.ServiceRoadBox = function(asset) {
    var serviceRoadValues = [
      [ 0, 'Tieoikeus'],
      [ 1, 'Tiekunnan osakkuus'],
      [ 2, 'LiVin hallinnoimalla maa-alueella'],
      [ 3, 'Kevyen liikenteen väylä'],
      [ 4, 'Tuntematon']
    ];
    var serviceRoadLegendTemplate = _.map(serviceRoadValues, function(serviceRoadValue) {
      return '<div class="legend-entry">' +
        '<div class="label">' + serviceRoadValue[1] + '</div>' +
        '<div class="symbol linear service-road-' + serviceRoadValue[0] + '" />' +
        '</div>';
    }).join('');

    var complementaryLinkCheckBox = asset.allowComplementaryLinks ? [
        '  <div class="panel-section roadLink-complementary-checkbox">' +
        '<div class="check-box-container">' +
        '<input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
        '</div>' +
        '</div>'
      ].join('') : '';


    var expandedTemplate = [
      '<div class="panel ' + asset.layerName +'">',
      '  <header class="panel-header expanded">',
      '    ' + asset.title + (asset.editControlLabels.showUnit ? ' ('+asset.unit+')': ''),
      '  </header>',
      '  <div class="panel-section panel-legend linear-asset-legend service-road-legend">',
      serviceRoadLegendTemplate,
      '  </div>',
      complementaryLinkCheckBox,
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var toolSelection = new ToolSelection([
      new Tool('Select', selectToolIcon, asset.selectedLinearAsset),
      new Tool('Cut', cutToolIcon, asset.selectedLinearAsset),
      new Tool('Rectangle', rectangleToolIcon, asset.selectedLinearAsset),
      new Tool('Polygon', polygonToolIcon, asset.selectedLinearAsset)
    ]);
    var editModeToggle = new EditModeToggleButton(toolSelection);
    var userRoles;

    var bindExternalEventHandlers = function() {
      eventbus.on('roles:fetched', function(roles) {
        userRoles = roles;
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium')  || _.contains(roles, 'serviceRoadMaintainer')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
          elements.expanded.append(editModeToggle.element);
        }
      });
      eventbus.on('application:readOnly', function(readOnly) {
        elements.expanded.find('.panel-header').toggleClass('edit', !readOnly);
      });
    };

    bindExternalEventHandlers();

    var element = $('<div class="panel-group service-road"/>')
      .append(elements.expanded)
      .hide();

    function show() {
      if (editModeToggle.hasNoRolesPermission(userRoles)) {
        editModeToggle.reset();
      } else {
        editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      }
      element.show();
    }

    function hide() {
      element.hide();
    }

    elements.expanded.find('#complementaryLinkCheckBox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('complementaryLinks:show');
      } else {
        if (applicationModel.isDirty()) {
          $(event.currentTarget).prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('complementaryLinks:hide');
        }
      }
    });

    eventbus.on('maintenanceRoad:activeComplementaryLayer', function() {
      elements.expanded.find('#complementaryLinkCheckBox').prop('checked', true);
    });

    return {
      title: asset.title,
      layerName: asset.layerName,
      element: element,
      show: show,
      hide: hide
    };
  };

  ActionPanelBoxes.AssetBox = function(selectedMassTransitStopModel) {
    var toolSelection = new ToolSelection([
      new Tool('Select', selectToolIcon, selectedMassTransitStopModel),
      new Tool('Add', setTitleTool(addToolIcon, 'Lisää pysäkki'), selectedMassTransitStopModel),
      new Tool('AddTerminal', setTitleTool(terminalToolIcon, 'Lisää terminaalipysäkki'), selectedMassTransitStopModel)
    ]);

    var editModeToggle = new EditModeToggleButton(toolSelection);

    var roadTypeLegend = [
        '  <div class="panel-section panel-legend road-link-legend">',
        '    <div class="legend-entry">',
        '      <div class="label">Valtion omistama</div>',
        '      <div class="symbol linear road"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Kunnan omistama</div>',
        '     <div class="symbol linear street"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Yksityisen omistama</div>',
        '     <div class="symbol linear private-road"/>',
        '   </div>',
        '   <div class="legend-entry">',
        '     <div class="label">Ei tiedossa tai kevyen liikenteen väylä</div>',
        '     <div class="symbol linear unknown"/>',
        '   </div>',
        '  </div>'
    ].join('');

    var constructionTypeLegend = [
      '  <div class="panel-section panel-legend linear-asset-legend construction-type-legend">',
      '    <div class="legend-entry">',
      '      <div class="label">Rakenteilla</div>',
      '      <div class="symbol linear construction-type-1"/>',
      '   </div>',
      '   <div class="legend-entry">',
      '     <div class="label">Suunnitteilla</div>',
      '     <div class="symbol linear construction-type-3"/>',
      '   </div>',
      '  </div>'
    ].join('');

    var roadLinkComplementaryCheckBox = [
      '  <div class="panel-section roadLink-complementary-checkbox">',
          '<div class="check-box-container">' +
            '<input id="complementaryCheckbox" type="checkbox" checked/> <lable>Näytä täydentävä geometria</lable>' +
          '</div>' +
      '</div>'
    ].join('');

    var expandedTemplate = [
      '<div class="panel">',
      '  <header class="panel-header expanded">',
      '    Joukkoliikenteen pysäkki',
      '  </header>',
      '  <div class="panel-section">',
      '    <div class="checkbox">',
      '      <label>',
      '        <input name="current" type="checkbox" checked> Voimassaolevat',
      '      </label>',
      '    </div>',
      '    <div class="checkbox">',
      '      <label>',
      '        <input name="future" type="checkbox"> Tulevat',
      '      </label>',
      '    </div>',
      '    <div class="checkbox">',
      '      <label>',
      '        <input name="past" type="checkbox"> K&auml;yt&ouml;st&auml; poistuneet',
      '      </label>',
      '    </div>',
      '    <div class="checkbox road-type-checkbox">',
      '      <label>',
      '        <input name="road-types" type="checkbox"> Hallinnollinen luokka',
      '      </label>',
      '    </div>',
      '  </div>',
      roadTypeLegend,
      constructionTypeLegend,
      roadLinkComplementaryCheckBox,
      '</div>'].join('');

    var elements = {
      expanded: $(expandedTemplate)
    };

    var bindDOMEventHandlers = function() {
      var validityPeriodChangeHandler = function(event) {
        executeOrShowConfirmDialog(function() {
          var el = $(event.currentTarget);
          var validityPeriod = el.prop('name');
          massTransitStopsCollection.selectValidityPeriod(validityPeriod, el.prop('checked'));
        });
      };

      elements.expanded.find('.checkbox').find('input[type=checkbox]').change(validityPeriodChangeHandler);
      elements.expanded.find('.checkbox').find('input[type=checkbox]').click(function(event) {
        if (applicationModel.isDirty()) {
          event.preventDefault();
        }
      });

      var expandedRoadTypeCheckboxSelector = elements.expanded.find('.road-type-checkbox').find('input[type=checkbox]');

      var roadTypeSelected = function(e) {
        var checked = e.currentTarget.checked;
        applicationModel.setRoadTypeShown(checked);
      };

      expandedRoadTypeCheckboxSelector.change(roadTypeSelected);

      elements.expanded.find('#complementaryCheckbox').on('change', function (event) {
        if ($(event.currentTarget).prop('checked')) {
          eventbus.trigger('roadLinkComplementaryBS:show');
        } else {
          if (applicationModel.isDirty()) {
            $(event.currentTarget).prop('checked', true);
            new Confirm();
          } else {
            eventbus.trigger('roadLinkComplementaryBS:hide');
          }
        }
      });

    };

    var toggleRoadType = function(bool) {
      var expandedRoadTypeCheckboxSelector = elements.expanded.find('.road-type-checkbox').find('input[type=checkbox]');

      elements.expanded.find('.road-link-legend').toggle(bool);
      elements.expanded.find('.construction-type-legend').toggle(bool);
      expandedRoadTypeCheckboxSelector.prop("checked", bool);
    };

    var bindExternalEventHandlers = function() {
      eventbus.on('validityPeriod:changed', function() {
        var toggleValidityPeriodCheckbox = function(validityPeriods, el) {
          $(el).prop('checked', validityPeriods[el.name]);
        };

        var checkboxes = $.makeArray(elements.expanded.find('input[type=checkbox]'));
        _.forEach(checkboxes, _.partial(toggleValidityPeriodCheckbox, massTransitStopsCollection.getValidityPeriods()));
      });

      eventbus.on('asset:saved asset:created', function(asset) {
        massTransitStopsCollection.selectValidityPeriod(asset.validityPeriod, true);
      }, this);

      eventbus.on('roles:fetched', function(roles) {
        if (_.contains(roles, 'operator') || _.contains(roles, 'premium') || _.isEmpty(roles) || _.contains(roles, 'busStopMaintainer')) {
          toolSelection.reset();
          elements.expanded.append(toolSelection.element);
          elements.expanded.append(editModeToggle.element);
        }
      });

      eventbus.on('road-type:selected', toggleRoadType);
    };

    bindDOMEventHandlers();

    bindExternalEventHandlers();

    toggleRoadType(true);

    var element = $('<div class="panel-group mass-transit-stops"/>')
      .append(elements.expanded)
      .hide();

    function show() {
      editModeToggle.toggleEditMode(applicationModel.isReadOnly());
      element.show();
    }

    function hide() {
      element.hide();
    }

    function setTitleTool(icon, title) {
      return icon.replace('/>', ' title="'+title+'"/>');
    }

    return {
      title: 'Joukkoliikenteen pysäkki',
      layerName: 'massTransitStop',
      element: element,
      show: show,
      hide: hide
    };
  };

  //TODO: Refactoring when UD 1080 is merged
  ActionPanelBoxes.LimitationBox = function (asset, labeling) {

    var className = _.kebabCase(asset.layerName);
    var element = $('<div class="panel-group point-asset ' + className + '"></div>').hide();

    var complementaryCheckBox = asset.allowComplementaryLinks ?
      '<div class="panel-section">' +
      '<div class="check-box-container">' +
      '<input id="complementaryCheckbox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
      '</div>' +
      '</div>' : '';

    var legendTemplate = _(asset.legendValues).map(function (val) {
      return '<div class="legend-entry">' +
        '<div class="label"><span>' + val.label + '</span> <img class="symbol" src="' + val.symbolUrl + '"/></div>' +
        '</div>';
    }).join('');

    var label = labeling ? labeling : [
        {index : 0,  labeling: 'Suurin sallittu massa'},
        {index : 1,  labeling: 'Yhdistelmän suurin sallittu massa'},
        {index : 2 , labeling: 'Suurin sallittu akselimassa'},
        {index : 3 , labeling: 'Suurin sallittu telimassa'}
      ];

    var labelingTemplate = _(label).map(function (value) {
      return '<div class="labeling-entry">' +
        '  <div class="limitation-'+value.index+'">' +
        value.labeling +
        '  </div>' +
        '</div>';
    }).join('');


    var labelTemplate =   '<div class="panel-section">' +
      '<div class="labelTemplate">' +
      labelingTemplate +
      '</div>' + '</div>' ;


    var panel = $('<div class="panel limitation-label-legend">' +
      '   <header class="panel-header expanded">' +
      asset.title +
      '   </header>' +
      '   <div class="panel-section panel-legend limit-legend">' +
      legendTemplate  +
      '   </div>' +
      labelTemplate +
      complementaryCheckBox +
      '</div>');

    element.append(panel);

    element.find('#complementaryCheckbox').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('withComplementary:show');
      } else {
        if (applicationModel.isDirty()) {
          $(event.currentTarget).prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('withComplementary:hide');
        }
      }
    });

    function show() {
      element.show();
    }

    function hide() {
      element.hide();
    }

    this.getElement = function () {
      return element;
    };

    return {
      title: asset.title,
      layerName: asset.layerName,
      element: element,
      allowComplementaryLinks: asset.allowComplementaryLinks,
      show: show,
      hide: hide
    };
  };

  ActionPanelBoxes.HeightLimitationBox = function (asset){
    var label = [
      {index: 1 , labeling: 'Suurin sallittu korkeus'}
    ];

    return new ActionPanelBoxes.LimitationBox(asset, label);
  };

  ActionPanelBoxes.WidthLimitationBox = function (asset) {
    var label = [
      {index: 1 , labeling: 'Suurin sallittu leveys'}
    ];

    return new ActionPanelBoxes.LimitationBox(asset, label);
  };



})(window.ActionPanelBoxes = window.ActionPanelBoxes || {});
