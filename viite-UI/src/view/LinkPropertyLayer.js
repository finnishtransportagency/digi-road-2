(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel,styler) {
    var layerName = 'linkProperty';
    var cachedLinkPropertyMarker = null;
    var cachedMarker = null;
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var eventListener = _.extend({running: false}, eventbus);
    var zoom = 7;
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;
    var indicatorVector = new ol.source.Vector({});
    var floatingMarkerVector = new ol.source.Vector({});
    var anomalousMarkerVector = new ol.source.Vector({});
    var calibrationPointVector = new ol.source.Vector({});

    var indicatorLayer = new ol.layer.Vector({
      source: indicatorVector
    });

    var floatingMarkerLayer = new ol.layer.Vector({
      source: floatingMarkerVector
    });

    var anomalousMarkerLayer = new ol.layer.Vector({
      source: anomalousMarkerVector
    });

    var calibrationPointLayer = new ol.layer.Vector({
      source: calibrationPointVector
    });

    map.addLayer(floatingMarkerLayer);
    map.addLayer(anomalousMarkerLayer);
    map.addLayer(calibrationPointLayer);
    map.addLayer(indicatorLayer);
    floatingMarkerLayer.setVisible(true);
    anomalousMarkerLayer.setVisible(true);
    calibrationPointLayer.setVisible(true);
    indicatorLayer.setVisible(true);

    var isAnomalousById = function(featureId){
      var anomalousMarkers = anomalousMarkerLayer.getSource().getFeatures();
      return !_.isUndefined(_.find(anomalousMarkers, function(am){
        return am.id === featureId;
      }));
    };

    var isFloatingById = function(featureId){
      var floatingMarkers = floatingMarkerLayer.getSource().getFeatures();
      return !_.isUndefined(_.find(floatingMarkers, function(fm){
        return fm.id === featureId;
      }));
    };

    /**
     * We declare the type of interaction we want the map to be able to respond.
     * A selected feature is moved to a new/temporary layer out of the default roadLayer.
     * This interaction is restricted to a double click.
     * @type {ol.interaction.Select}
     */
    var selectDoubleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      //multi: true,
      //This will limit the interaction to the specific layer, in this case the layer where the roadAddressLinks are drawn
      layer: roadLayer.layer,
      //Limit this interaction to the doubleClick
      condition: ol.events.condition.doubleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });

    //We add the defined interaction to the map.
    map.addInteraction(selectDoubleClick);

    /**
     * We now declare what kind of custom actions we want when the interaction happens.
     * Note that 'select' is triggered when a feature is either selected or deselected.
     * The event holds the selected features in the events.selected and the deselected in event.deselected.
     */
    selectDoubleClick.on('select',function(event) {
      var visibleFeatures = getVisibleFeatures(true, true, true);
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }

      //Since the selected features are moved to a new/temporary layer we just need to reduce the roadlayer's opacity levels.
      if (event.selected.length !== 0) {
        if (roadLayer.layer.getOpacity() === 1) {
          roadLayer.layer.setOpacity(0.2);
          floatingMarkerLayer.setOpacity(0.2);
          anomalousMarkerLayer.setOpacity(0.2);
        }

        selectedLinkProperty.close();
        var selection = _.find(event.selected, function(selectionTarget){
          return !_.isUndefined(selectionTarget.roadLinkData);
        });
        if (selection.roadLinkData.roadLinkType === -1 &&
          ('all' === applicationModel.getSelectionType() || 'floating' === applicationModel.getSelectionType()) &&
          applicationModel.isReadOnly()) {
          selectedLinkProperty.openFloating(selection.roadLinkData.linkId, selection.roadLinkData.id, visibleFeatures);
        } else {
          selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true, visibleFeatures);
        }
      } else if (event.selected.length === 0 && event.deselected.length !== 0){
        selectedLinkProperty.close();
        roadLayer.layer.setOpacity(1);
        floatingMarkerLayer.setOpacity(1);
        anomalousMarkerLayer.setOpacity(1);
      }
    });

//This will control the double click zoom when there is no selection
    map.on('dblclick', function(event) {
      _.defer(function(){
        if(selectDoubleClick.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13){
          map.getView().setZoom(map.getView().getZoom()+1);
        }
      });
    });

    /**
     * We declare the type of interaction we want the map to be able to respond.
     * A selected feature is moved to a new/temporary layer out of the default roadLayer.
     * This interaction is restricted to a single click (there is a 250 ms enforced
     * delay between single clicks in order to diferentiate from double click).
     * @type {ol.interaction.Select}
     */
    var selectSingleClick = new ol.interaction.Select({
      //Multi is the one en charge of defining if we select just the feature we clicked or all the overlaping
      //multi: true,
      //This will limit the interaction to the specific layer, in this case the layer where the roadAddressLinks are drawn
      layer: [roadLayer.layer, floatingMarkerLayer, anomalousMarkerLayer],
      //Limit this interaction to the singleClick
      condition: ol.events.condition.singleClick,
      //The new/temporary layer needs to have a style function as well, we define it here.
      style: function(feature, resolution) {
        return styler.generateStyleByFeature(feature.roadLinkData,map.getView().getZoom());
      }
    });

    //We add the defined interaction to the map.
    map.addInteraction(selectSingleClick);

    /**
     * We now declare what kind of custom actions we want when the interaction happens.
     * Note that 'select' is triggered when a feature is either selected or deselected.
     * The event holds the selected features in the events.selected and the deselected in event.deselected.
     *
     * In this particular case we are fetching every roadLinkAddress and anomaly marker in view and
     * sending them to the selectedLinkProperty.open for further processing.
     */
    selectSingleClick.on('select',function(event) {
      var visibleFeatures = getVisibleFeatures(true,true,true);
      if (selectDoubleClick.getFeatures().getLength() !== 0) {
        selectDoubleClick.getFeatures().clear();
      }
      var selection = _.find(event.selected, function (selectionTarget) {
        return !_.isUndefined(selectionTarget.roadLinkData);
      });

      //if event selected is undefined, in this case if is the map
      if (!_.isUndefined(selection)) {

        //Disable selection of other roads different than anomaly or floating
        if (!applicationModel.isReadOnly() && selection.roadLinkData.anomaly !== 1 && selection.roadLinkData.roadLinkType !== -1) {
          //Unselect all roads selected
          selectSingleClick.getFeatures().remove(selection);
          //selectSingleClick.getFeatures().clear();

        } else if(!selectedLinkProperty.featureExistsInSelection(selection) && (typeof selection.roadLinkData.linkId !== 'undefined')) {
          if (!applicationModel.isReadOnly() && applicationModel.getSelectionType() === 'floating' && selection.roadLinkData.roadLinkType === -1) {
            //Verify if the next Floating selected as the same RoadNumber, roadPartNumber and TrackCode as the previous selected
            if (selectedLinkProperty.isFloatingHomogeneous(selection)) {
              var data = {
                'selectedFloatings': _.reject(selectedLinkProperty.getFeaturesToKeep(), function (feature) {
                  return selection.roadLinkData.roadLinkType !== -1;
                }), 'selectedLinkId': selection.roadLinkData.linkId
              };
              eventbus.trigger('linkProperties:additionalFloatingSelected', data);
            } else {
              unhighlightFeatureByLinkId(selection.roadLinkData.linkId);
              return new ModalConfirm("Et voi valita tätä, koska tie, tieosa tai ajorata on eri kuin aikaisemmin valitulla");
            }
          }
          //Since the selected features are moved to a new/temporary layer we just need to reduce the roadlayer's opacity levels.
          if (!_.isUndefined(selection)) {
            if (event.selected.length !== 0) {
              if (roadLayer.layer.getOpacity() === 1) {
                roadLayer.layer.setOpacity(0.2);
                floatingMarkerLayer.setOpacity(0.2);
                anomalousMarkerLayer.setOpacity(0.2);
              }

              //OL3
              if(!applicationModel.isReadOnly() && applicationModel.getSelectionType() === 'all' && selection.roadLinkData.roadLinkType === -1){
                applicationModel.toggleSelectionTypeFloating();
              }

              selectedLinkProperty.close();
              if (selection.roadLinkData.roadLinkType === -1 &&
                ('all' === applicationModel.getSelectionType() || 'floating' === applicationModel.getSelectionType()) &&
                applicationModel.isReadOnly()) {
                selectedLinkProperty.openFloating(selection.roadLinkData.linkId, selection.roadLinkData.id, visibleFeatures);
              } else {
                if (isAnomalousById(selection.id) || isFloatingById(selection.id)) {
                  selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, true, visibleFeatures);
                } else {
                  selectedLinkProperty.open(selection.roadLinkData.linkId, selection.roadLinkData.id, false, visibleFeatures);
                }
              }
            }
          }
        }
      } else if (event.selected.length === 0 && event.deselected.length !== 0) {
        selectedLinkProperty.close();
        roadLayer.layer.setOpacity(1);
        floatingMarkerLayer.setOpacity(1);
        anomalousMarkerLayer.setOpacity(1);
      }
    });

    /**
     * Simple method that will add various open layers 3 features to a selection.
     * @param ol3Features
     */
    var addFeaturesToSelection = function (ol3Features) {
      _.each(ol3Features, function(feature){
        selectSingleClick.getFeatures().push(feature);
      });
    };

    /**
     * Event triggred by the selectedLinkProperty.open() returning all the open layers 3 features
     * that need to be included in the selection.
     */
    eventbus.on('linkProperties:ol3Selected',function(ol3Features){
      selectSingleClick.getFeatures().clear();
      addFeaturesToSelection(ol3Features);
    });

    var getVisibleFeatures = function(withRoads, withAnomalyMarkers, withFloatingMarkers){
      var extent = map.getView().calculateExtent(map.getSize());
      var visibleRoads = withRoads ? roadLayer.layer.getSource().getFeaturesInExtent(extent) : [];
      var visibleAnomalyMarkers =  withAnomalyMarkers ? anomalousMarkerLayer.getSource().getFeaturesInExtent(extent) : [];
      var visibleFloatingMarkers =  withFloatingMarkers ? floatingMarkerLayer.getSource().getFeaturesInExtent(extent) : [];
      return visibleRoads.concat(visibleAnomalyMarkers).concat(visibleFloatingMarkers);
    };

    /**
     * This is remove all the features from all the selections.
     */
    var clearHighlights = function(){
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
      if(selectSingleClick.getFeatures().getLength() !== 0){
        selectSingleClick.getFeatures().clear();
      }
    };

    /*var unselectAllRoadLinks = function(options) {
     // we'll want an option to supress notification here
     var layers = this.layers || [this.layer],
     layer, feature, l, numExcept;
     for(l=0; l<layers.length; ++l) {
     layer = layers[l];
     numExcept = 0;
     //layer.selectedFeatures is null when layer is destroyed and
     //one of it's preremovelayer listener calls setLayer
     //with another layer on this control
     if(layer.selectedFeatures !== null) {
     if(applicationModel.isActiveButtons() && layer.selectedFeatures.length > numExcept)
     {
     return Confirm();
     }else {
     while (layer.selectedFeatures.length > numExcept) {
     feature = layer.selectedFeatures[numExcept];
     if (!options || options.except != feature) {
     this.unselect(feature);
     } else {
     ++numExcept;
     }
     }
     }
     }
     }
     };*/

    var clearLayers = function(){
      floatingMarkerLayer.getSource().clear();
      anomalousMarkerLayer.getSource().clear();
      calibrationPointLayer.getSource().clear();
      indicatorLayer.getSource().clear();
    };

    /*var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
     onSelect: selectRoadLink,
     onUnselect: unselectRoadLink,
     unselectAll: unselectAllRoadLinks
     });
     roadLayer.layer.events.register("beforefeatureselected", this, function(event){
     if(applicationModel.isActiveButtons()) {
     var feature = event.feature.attributes;
     if (applicationModel.isReadOnly() || applicationModel.getSelectionType() === 'all') {
     return true;
     } else {
     if (applicationModel.getSelectionType() === 'floating') {
     if (feature.roadLinkType !== -1) {
     me.displayConfirmMessage();
     return false;
     } else {
     return true;
     }
     }
     if (applicationModel.getSelectionType() === 'unknown') {
     if (feature.roadLinkType !== 0 && feature.anomaly !== 1 && !applicationModel.isActiveButtons()) {
     me.displayConfirmMessage();
     return false;
     } else {
     return true;
     }
     }
     }
     }
     });*/



    /*map.addControl(selectControl);
     var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);
     this.selectControl = selectControl;
     */
    /**
     * This will remove all the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick
     */
    var deactivateSelection = function() {
      map.removeInteraction(selectDoubleClick);
      map.removeInteraction(selectSingleClick);
    };

    /**
     * This will add all the following interactions from the map:
     * -selectDoubleClick
     * -selectSingleClick
     */
    var activateSelection = function () {
      map.addInteraction(selectDoubleClick);
      map.addInteraction(selectSingleClick);
    };

    /* var unselectRoadLink = function() {
     currentRenderIntent = 'default';
     selectedLinkProperty.close();
     _.map(roadLayer.layer.features,function (feature){
     if(feature.data.gapTransfering) {
     feature.data.gapTransfering = false;
     feature.attributes.gapTransfering = false;
     feature.data.anomaly = feature.data.prevAnomaly;
     feature.attributes.anomaly = feature.attributes.prevAnomaly;
     }
     });
     indicatorLayer.clearMarkers();
     unhighlightFeatures();
     roadLayer.redraw();
     };
     */

    var unselectRoadLink = function() {
      selectedLinkProperty.close();
      clearHighlights();
      indicatorLayer.getSource().clear();
      //OL2
      _.map(roadLayer.layer.features,function (feature){
        if(feature.data.gapTransfering) {
          feature.data.gapTransfering = false;
          feature.attributes.gapTransfering = false;
          feature.data.anomaly = feature.data.prevAnomaly;
          feature.attributes.anomaly = feature.attributes.prevAnomaly;
        }
      });
    };

    var highlightFeatures = function() {
      clearHighlights();
      var featuresToHighlight = [];
      _.each(roadLayer.layer.features, function(feature) {
        var gapTransfering = x.data.gapTransfering;
        var canIHighlight = !_.isUndefined(feature.attributes.linkId) ? selectedLinkProperty.isSelectedByLinkId(feature.attributes.linkId) : selectedLinkProperty.isSelectedById(feature.attributes.id);
        if(gapTransfering || canIHighlight){
          featuresToHighlight.push(feature);
          //OL2
          //selectControl.highlight(x);
          //} else {
          //selectControl.unhighlight(x);
        }
      });
      if(featuresToHighlight.length !== 0)
        addFeaturesToSelection(featuresToHighlight);
    };

    var highlightFeatureByLinkId = function (linkId) {
      _.each(roadLayer.layer.features, function(feature) {
        if(feature.attributes.linkId == linkId){
          selectControl.highlight(feature);
        }
      });
    };

    var unhighlightFeatureByLinkId = function (linkId) {
      _.each(roadLayer.layer.features, function(feature) {
        if(feature.attributes.linkId == linkId){
          feature.clear();
        }
      });
    };

    var unhighlightFeatures = function() {
      _.each(roadLayer.layer.features, function(feature) {
        feature.clear();
      });
    };

    var draw = function() {
      cachedLinkPropertyMarker = new LinkPropertyMarker(selectedLinkProperty);
      cachedMarker = new LinkPropertyMarker(selectedLinkProperty);
      deactivateSelection();
      var roadLinks = roadCollection.getAll();

      if(floatingMarkerLayer.getSource() !== null)
        floatingMarkerLayer.getSource().clear();
      if(anomalousMarkerLayer.getSource() !== null)
        anomalousMarkerLayer.getSource().clear();

      /*roadLayer.drawRoadLinks(roadLinks, zoom);
       drawDashedLineFeaturesIfApplicable(roadLinks);*/

      if(map.getView().getZoom() > zoomlevels.minZoomForAssets) {
        var floatingRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.roadLinkType === -1;
        });

        var anomalousRoadMarkers = _.filter(roadLinks, function(roadlink) {
          return roadlink.anomaly === 1;
        });

        _.each(floatingRoadMarkers, function(floatlink) {
          var marker = cachedLinkPropertyMarker.createMarker(floatlink);
          floatingMarkerLayer.getSource().addFeature(marker);
        });

        _.each(anomalousRoadMarkers, function(anomalouslink) {
          var marker = cachedMarker.createMarker(anomalouslink);
          anomalousMarkerLayer.getSource().addFeature(marker);

        });
      }

      if (map.getView().getZoom() > zoomlevels.minZoomForAssets) {
        var actualPoints =  me.drawCalibrationMarkers(calibrationPointLayer.source, roadLinks);
        _.each(actualPoints, function(actualPoint) {
          var calMarker = new CalibrationPoint(actualPoint.point);
          calibrationPointLayer.getSource().addFeature(calMarker.getMarker(true));
        });
      }
      /*if(!_.isUndefined(action) && _.isEqual(action, applicationModel.actionCalculated)){
       redrawSelected(action);
       } else {
       redrawSelected();
       }*/
      activateSelection();
      eventbus.trigger('linkProperties:available');
    };

    /* var createMouseClickHandler = function(floatlink) {
     return function(event){
     if(floatlink.anomaly !== 1) {
     if(applicationModel.getSelectionType() !== 'floating' && floatlink.roadLinkType !== -1 || applicationModel.getSelectionType() === 'all'){
     selectControl.unselectAll();
     }
     }
     var feature = _.find(roadLayer.layer.features, function (feat) {
     return feat.attributes.linkId === floatlink.linkId;
     });
     if(event.type === 'click'){
     selectControl.select(_.assign({singleLinkSelect: false}, feature));
     } else if( event.type === 'dblclick'){
     selectControl.select(_.assign({singleLinkSelect: true}, feature));
     } else {
     selectControl.unselectAll();
     }
     };
     };*/

    /*this.refreshView = function() {
     // Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
     zoom = 11 - Math.round(Math.log(map.getResolution()) * Math.LOG2E);
     roadCollection.fetch(map.getExtent(), zoom);
     };*/

    this.refreshView = function() {
      roadCollection.fetch(map.getExtent(), 11);
      roadLayer.layer.changed();
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var vectorLayer = new ol.layer.Vector();
    vectorLayer.setOpacity(1);
    vectorLayer.setVisible(true);

    /*var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
     return _.flatten(_.map(roadLinks, function(roadLink) {
     var points = _.map(roadLink.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var attributes = {
     dashedLineFeature: roadLink[dashedLineFeature],
     linkId: roadLink.linkId,
     type: 'overlay',
     linkType: roadLink.linkType,
     zIndex: 1
     };
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
     }));
     };

     var unknownFeatureSizeLookup = {
     9: { strokeWidth: 3, pointRadius: 0 },
     10: { strokeWidth: 5, pointRadius: 10 },
     11: { strokeWidth: 7, pointRadius: 14 },
     12: { strokeWidth: 10, pointRadius: 16 },
     13: { strokeWidth: 10, pointRadius: 16 },
     14: { strokeWidth: 14, pointRadius: 22 },
     15: { strokeWidth: 14, pointRadius: 22 }
     };

     var browseStyle = new OpenLayers.Style(OpenLayers.Util.applyDefaults());
     var browseStyleMap = new OpenLayers.StyleMap({ default: browseStyle });
     browseStyleMap.addUniqueValueRules('default', 'level', unknownFeatureSizeLookup, applicationModel.zoom);
     */

     var typeFilter = function(type) {
     return new OpenLayers.Filter.Comparison(
     { type: OpenLayers.Filter.Comparison.EQUAL_TO, property: 'type', value: type });
     };

     /*
     var unknownLimitStyleRule = new OpenLayers.Rule({
     filter: typeFilter('roadAddressAnomaly'),
     symbolizer: { externalGraphic: 'images/speed-limits/unknown.svg' }
     });
     */
     /*
     browseStyle.addRules([unknownLimitStyleRule]);
     var vectorLayer = new OpenLayers.Layer.Vector(layerName, { styleMap: browseStyleMap });
     vectorLayer.setOpacity(1);
     vectorLayer.setVisibility(true);*/

    /*    var drawDashedLineFeatures = function(roadLinks) {
     var dashedRoadClasses = [7, 8, 9, 10];
     var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(dashedRoadClasses, roadLink.roadClass);
     });
     roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'functionalClass'));
     };

     var drawUnderConstructionFeatures = function(roadLinks) {
     var constructionTypeValues = [1];
     var unknownType = 'unknownConstructionType';
     var dashedUnknownUnderConstructionRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(constructionTypeValues, roadLink.constructionType) && roadLink.anomaly === 1;
     });
     var type = 'constructionType';
     var dashedUnderConstructionRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(constructionTypeValues, roadLink.constructionType) && roadLink.roadClass === 99 && roadLink.anomaly === 0;
     });
     roadLayer.layer.addFeatures(createDarkDashedLineFeatures(dashedUnknownUnderConstructionRoadLinks, unknownType));
     roadLayer.layer.addFeatures(createDarkDashedLineFeatures(dashedUnderConstructionRoadLinks, type));
     };

     var drawDashedLineFeaturesForType = function(roadLinks) {
     var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
     var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
     return _.contains(dashedLinkTypes, roadLink.linkType);
     });
     roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
     };
     var drawBorderLineFeatures = function(roadLinks) {
     var adminClass = 'Municipality';
     var roadClasses = [1,2,3,4,5,6,7,8,9,10,11];
     var borderLineFeatures = _.filter(roadLinks, function(roadLink) {
     return _.contains(adminClass, roadLink.administrativeClass) && _.contains(roadClasses, roadLink.roadClass) && roadLink.roadLinkType !== -1 && !(roadLink.roadLinkType === -1 && roadLink.roadClasses === 3);
     });
     var features = createBorderLineFeatures(borderLineFeatures, 'functionalClass');
     roadLayer.layer.addFeatures(features);
     };
     var createDarkDashedLineFeatures = function(roadLinks, type){
     return darkDashedLineFeatures(roadLinks, type).concat(calculateMidPointForMarker(roadLinks, type));
     };
     var darkDashedLineFeatures = function(roadLinks, darkDashedLineFeature) {
     return _.flatten(_.map(roadLinks, function(roadLink) {
     var points = _.map(roadLink.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var attributes = {
     dashedLineFeature: roadLink[darkDashedLineFeature],
     linkId: roadLink.linkId,
     type: 'overlay-dark',
     linkType: roadLink.linkType,
     zIndex: 1
     };
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
     }));
     };
     var calculateMidPointForMarker = function(roadLinks, type){
     return _.map(roadLinks, function(link) {
     var points = _.map(link.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var road = new OpenLayers.Geometry.LineString(points);
     var signPosition = GeometryUtils.calculateMidpointOfLineString(road);
     var attributes = {type: type, linkId: link.linkId};
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(signPosition.x, signPosition.y), attributes);
     });
     };
     var createBorderLineFeatures = function(roadLinks) {
     return _.flatten(_.map(roadLinks, function(roadLink) {
     var points = _.map(roadLink.points, function(point) {
     return new OpenLayers.Geometry.Point(point.x, point.y);
     });
     var attributes = {
     linkId: roadLink.linkId,
     type: 'underlay',
     linkType: roadLink.roadLinkType
     };
     return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
     }));
     };*/

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.features, function (feature) {
        return selectedLinkProperty.isSelectedByLinkId(feature.attributes.linkId);
      });
    };

    var reselectRoadLink = function() {
      me.activateSelection();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var features = getSelectedFeatures();
      var indicators = jQuery.extend(true, [], indicatorLayer.markers);
      indicatorLayer.clear();
      if(indicators.length !== 0){
        _.forEach(indicators, function(indicator){
          indicatorLayer.addMarker(createIndicatorFromBounds(indicator.bounds, indicator.div.innerText));
        });
      }
      if (!_.isEmpty(features)) {
        //currentRenderIntent = 'select';
        selectControl.select(_.first(features));
        if(_.isEqual(applicationModel.getCurrentAction(), applicationModel.actionCalculated) || !_.isEmpty(roadCollection.getChangedIds())){
          _.each(roadCollection.getChangedIds(), function (id){
            highlightFeatureByLinkId(id);
          });
        }
        else{
          highlightFeatures();
        }
      }
      selectControl.onSelect = originalOnSelectHandler;
      if (selectedLinkProperty.isDirty()) {
        me.deactivateSelection();
      }
    };


    /* var prepareRoadLinkDraw = function() {
     me.deactivateSelection();
     };

     var drawDashedLineFeaturesIfApplicable = function (roadLinks) {
     drawDashedLineFeatures(roadLinks);
     drawBorderLineFeatures(roadLinks);
     drawUnderConstructionFeatures(roadLinks);
     };
     */


    var handleLinkPropertyChanged = function(eventListener) {
      //OL2
      //redrawSelected();
      deactivateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      activateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      roadLayer.layer.setOpacity(1);
      floatingMarkerLayer.setOpacity(1);
      anomalousMarkerLayer.setOpacity(1);
      //deactivateSelection();
      if(selectDoubleClick.getFeatures().getLength() !== 0){
        selectDoubleClick.getFeatures().clear();
      }
    };

    this.refreshView = function() {
      // Generalize the zoom levels as the resolutions and zoom levels differ between map tile sources
      roadCollection.fetch(map.getExtent(), 11);
      roadLayer.layer.changed();
    };

    this.layerStarted = function(eventListener) {
      indicatorLayer.setZIndex(1000);
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);

      /*
       eventListener.listenTo(eventbus, 'linkProperties:multiSelected', function(link) {
       if (!_.isEmpty(selectedLinkProperty.get())){
       var feature = _.find(roadLayer.layer.features, function (feature) {
       return link.linkId !== 0 && feature.attributes.linkId === link.linkId;
       });
       if (feature) {
       _.each(selectControl.layer.selectedFeatures, function (selectedFeature) {
       if (selectedFeature.attributes.linkId !== feature.attributes.linkId) {
       selectControl.select(feature);
       }
       });
       }
       }
       clearIndicators();
       });
       */
      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        var features = [];
        _.each(roadLayer.layer.getSource().getFeatures(), function(feature){
          _.each(link, function (featureLink){
            if(featureLink.linkId !== 0 && feature.roadLinkData.linkId === featureLink.linkId){
              return features.push(feature);
            }
          });
        });

        if (features) {
          addFeaturesToSelection(features);
        }
        clearIndicators();
      });

      eventListener.listenTo(eventbus, 'linkProperties:reselect', reselectRoadLink);
      eventListener.listenTo(eventbus, 'roadLinks:drawAfterGapCanceling', function() {
        currentRenderIntent = 'default';
        _.map(roadLayer.layer.features,function (feature){
          if(feature.data.gapTransfering) {
            feature.data.gapTransfering = false;
            feature.attributes.gapTransfering = false;
            feature.data.anomaly = feature.data.prevAnomaly;
            feature.attributes.anomaly = feature.attributes.prevAnomaly;
          }
        });
        unhighlightFeatures();
        roadLayer.redraw();
        var current = selectedLinkProperty.get();
        _.forEach(current, function(road){
          var feature = _.find(roadLayer.layer.features, function (feature) {
            return road.linkId !== 0 && feature.attributes.linkId === road.linkId;
          });
          if (feature) {
            _.each(selectControl.layer.selectedFeatures, function (selectedFeature) {
              if (selectedFeature.attributes.linkId !== feature.attributes.linkId) {
                selectControl.select(feature);
              }
            });
          }
        });
        //OL2
        //indicatorLayer.clearMarkers();
        indicatorLayer.getSource().clear();
      });

      eventListener.listenTo(eventbus, 'roadLinks:fetched', draw);
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', draw);
      eventListener.listenTo(eventbus, 'linkProperties:updateFailed', cancelSelection);
      eventListener.listenTo(eventbus, 'adjacents:nextSelected', function(sources, adjacents, targets) {
        //OL3
        //redrawNextSelectedTarget(targets, adjacents);
        //drawIndicators(adjacents);
        //selectedLinkProperty.addTargets(targets, adjacents);
        applicationModel.addSpinner();
        if(applicationModel.getCurrentAction()!==applicationModel.actionCalculated){
          drawIndicators(adjacents);
          selectedLinkProperty.addTargets(targets, adjacents);
        }
        redrawNextSelectedTarget(targets, adjacents);
      });
      eventListener.listenTo(eventbus, 'adjacents:added adjacents:aditionalSourceFound', function(sources,targets, aditionalLinkId){
        drawIndicators(targets);
        /*
         _.map(_.rest(selectedLinkProperty.getFeaturesToKeep()), function (roads){
         editFeatureDataForGreen(roads);
         highlightFeatureByLinkId(roads.linkId);
         });
         highlightFeatureByLinkId(aditionalLinkId);
         */
      });

      eventListener.listenTo(eventbus, 'adjacents:floatingAdded', function(floatings){
        drawIndicators(floatings);
      });
      eventListener.listenTo(eventbus, 'adjacents:roadTransfer', function(newRoads,changedIds){
        var roadLinks = roadCollection.getAll();
        var afterTransferLinks=  _.filter(roadLinks, function(roadlink){
          return !_.contains(changedIds, roadlink.linkId.toString());
        });
        _.map(newRoads, function(road){
          afterTransferLinks.push(road);
        });
        //OL2
        //indicatorLayer.clearMarkers();
        indicatorLayer.getSource().clear();
        roadCollection.setTmpRoadAddresses(afterTransferLinks);
        roadCollection.setChangedIds(changedIds);
        applicationModel.setCurrentAction(applicationModel.actionCalculated);
        selectedLinkProperty.cancelAfterSiirra(applicationModel.actionCalculated, changedIds);
      });
      /*
       eventbus.on('linkProperties:reselectRoadLink', function(){
       reselectRoadLink();
       roadLayer.redraw();
       });
       */

      eventListener.listenTo(eventbus, 'roadLink:editModeAdjacents', function() {
        if (applicationModel.isReadOnly() && !applicationModel.isActiveButtons()) {
          //OL2
          //indicatorLayer.clearMarkers();
          indicatorLayer.getSource().clear();
          var floatingsLinkIds = _.map(_.filter(selectedLinkProperty.getFeaturesToKeep(), function (feature) {
            return feature.roadLinkType == -1;
          }), function (floating) {
            return floating.linkId;
          });
          _.defer(function () {
            _.map(roadLayer.layer.features, function (feature) {
              if (_.contains(floatingsLinkIds, feature.attributes.linkId)) {
                selectControl.select(feature);
              }
            });
          });
        } else {
          var selectedFloatings = _.filter(selectedLinkProperty.get(), function (features) {
            return features.roadLinkType == -1;
          });
          _.each(selectedFloatings, function(sf){
            selectedLinkProperty.getFeaturesToKeep().push(sf);
          });
        }
      });

      eventListener.listenTo(eventbus, 'roadLinks:deleteSelection', function () {
        prepareRoadLinkDraw();
      });

      eventListener.listenTo(eventbus, 'roadLinks:unSelectIndicators', function (originalFeature) {
        prepareRoadLinkDraw();
        clearIndicators();
        selectControl.unselectAll();
        roadCollection.getAll();
        if (applicationModel.getSelectionType() !== 'floating') {
          var features = [];
          var extractedLinkIds = _.map(originalFeature,function(of){
            return of.linkId;
          });
          _.each(roadLayer.layer.features, function (feature) {
            if (!_.contains(extractedLinkIds, feature.data.linkId) && feature.data.roadLinkType === -1){
              features.push(feature);
            }
          });

          if (!_.isEmpty(features)) {
            currentRenderIntent = 'select';
            selectControl.select(_.first(features));
            highlightFeatures();
          }
        }
      });

      /*
       eventListener.listenTo(eventbus, 'linkProperties:cancelled', unselectRoadLink);
       };


       */
      var clearIndicators = function () {
        //OL2
        //indicatorLayer.clearMarkers();
        indicatorLayer.getSource().clear();
      };

      eventListener.listenTo(eventListener, 'map:clearLayers', clearLayers);
    };

    var drawIndicators= function(links){
      indicatorLayer.getSource().clear();
      var indicators = me.mapOverLinkMiddlePoints(links, function(link, middlePoint) {
        var bounds = ol.extent.boundingExtent([middlePoint.x, middlePoint.y, middlePoint.x, middlePoint.y]);
        return createIndicatorFromBounds(bounds, link.marker);
      });
      _.forEach(indicators, function(indicator){
        indicatorLayer.getSource().addFeature(indicator);
      });
    };

    var createIndicatorFromBounds = function(bounds, marker) {
      var markerTemplate = _.template('<span class="marker" style="margin-left: -1em; margin-top: -1em; position: absolute;"><%= marker %></span>');
      var box = new OpenLayers.Marker.Box(bounds, "00000000");
      $(box.div).html(markerTemplate({'marker': marker}));
      $(box.div).css('overflow', 'visible');
      return box;
    };

    var handleMapClick = function (){
      if(!applicationModel.isActiveButtons()){
        selectedLinkProperty.cancel();
        selectedLinkProperty.close();
      }

    };

    var cancelSelection = function() {
      //OL2
      //if(!applicationModel.isActiveButtons()) {
      selectedLinkProperty.cancel();
      selectedLinkProperty.close();
      unselectRoadLink();
      //}
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      me.refreshView();
    };

    var redrawSelected = function(action) {
      var selectedRoadLinks = [];
      if(!applicationModel.isActiveButtons()){
        roadLayer.layer.removeFeatures(getSelectedFeatures());
      }
      if((!_.isUndefined(action) && _.isEqual(action, applicationModel.actionCalculated)) || !_.isEmpty(roadCollection.getAllTmp())){
        selectedRoadLinks = roadCollection.getAllTmp();
      } else {
        selectedRoadLinks = selectedLinkProperty.get();
      }
      _.each(selectedRoadLinks,  function(selectedLink) { roadLayer.drawRoadLink(selectedLink); });
      drawDashedLineFeaturesIfApplicable(selectedRoadLinks);
      me.drawSigns(roadLayer.layer, selectedRoadLinks);
      reselectRoadLink();
    };

    var redrawNextSelectedTarget= function(targets, adjacents) {
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).data.gapTransfering = true;
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).attributes.gapTransfering = true;
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).data.anomaly = 0;
      _.find(roadLayer.layer.features, function(feature) {
        return targets !== 0 && feature.attributes.linkId === targets;
      }).attributes.anomaly = 0;
      reselectRoadLink();
      draw();
      if (selectedLinkProperty.getFeaturesToHighlight().length > 1 && applicationModel.getSelectionType() === 'unknown') {
        selectedLinkProperty.getFeaturesToHighlight().forEach(function (fml) {
          highlightFeatureByLinkId(fml.data.linkId);
        });
      }
    };

    var editFeatureDataForGreen = function (targets) {
      var features =[];
      if(targets !== 0){
        _.map(roadLayer.layer.features, function(feature){
          if(feature.attributes.linkId == targets){
            feature.attributes.prevAnomaly = feature.attributes.anomaly;
            feature.data.prevAnomaly = feature.data.anomaly;
            feature.attributes.gapTransfering = true;
            feature.data.gapTransfering = true;
            selectedLinkProperty.getFeaturesToKeep().push(feature.data);
            features.push(feature);
            roadCollection.addPreMovedRoadAddresses(feature.data);
          }
        });
      }
      if(features.length === 0)
        return undefined;
      else return _.first(features);
    };

    eventbus.on('linkProperties:highlightAnomalousByFloating', function(){
      highlightAnomalousFeaturesByFloating();
    });

    var highlightAnomalousFeaturesByFloating = function() {
      _.find(roadLayer.layer.getSource().getFeatures(), function(anfeature){
        if(anfeature.roadLinkData.anomaly === 1){
          selectSingleClick.getFeatures().push(anfeature);
          roadLayer.layer.setOpacity(1);
          floatingMarkerLayer.setOpacity(1);
          anomalousMarkerLayer.setOpacity(1);
        }
      });
    };

    eventbus.on('linkProperties:unselectAllFeatures', function(){
      selectControl.unselectAll();
    });


    var show = function(map) {
      vectorLayer.setVisible(true);
      //eventListener.listenTo(eventbus, 'map:clicked', cancelSelection);
    };

    var hideLayer = function() {
      unselectRoadLink();
      me.stop();
      me.hide();
    };

    me.layerStarted(eventListener);

    return {
      show: show,
      hide: hideLayer,
      deactivateSelection: deactivateSelection,
      activateSelection: activateSelection,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
