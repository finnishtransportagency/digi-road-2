(function(application) {
  application.start = function(customBackend, withTileMaps, isExperimental) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ? true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var instructionsPopup = new InstructionsPopup($('.digiroad2'));

    var models = {
      roadCollection: roadCollection,
      selectedLinkProperty: selectedLinkProperty,
      linkPropertiesModel: linkPropertiesModel
    };

    window.applicationModel = new ApplicationModel([
      selectedLinkProperty]);

    EditModeDisclaimer.initialize(instructionsPopup);

    var assetGroups = groupAssets(
      linkPropertiesModel);

    eventbus.on('layer:selected', function(layer) {
      // assetSelectionMenu.select(layer);
    });

    NavigationPanel.initialize(
      $('#map-tools'),
      new SearchBox(
          instructionsPopup,
          new LocationSearch(backend, window.applicationModel)
      ),
      assetGroups
    );

    backend.getUserRoles();
    backend.getStartupParametersWithCallback(function (startupParameters) {
      startApplication(backend, models, tileMaps, startupParameters);
    });
  };

  var startApplication = function(backend, models, withTileMaps, startupParameters) {
    setupProjections();
    var map = setupMap(backend, models, withTileMaps, startupParameters);
    new URLRouter(map, backend, models);
    eventbus.trigger('application:initialized');
  };

  var localizedStrings;

  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var bindEvents = function(linearAssetSpecs, pointAssetSpecs) {
    var singleElementEventNames = _.pluck(linearAssetSpecs, 'singleElementEventCategory');
    var multiElementEventNames = _.pluck(linearAssetSpecs, 'multiElementEventCategory');
    var linearAssetSavingEvents = _.map(singleElementEventNames, function(name) { return name + ':saving'; }).join(' ');
    var pointAssetSavingEvents = _.map(pointAssetSpecs, function (spec) { return spec.layerName + ':saving'; }).join(' ');
    eventbus.on('asset:saving asset:creating speedLimit:saving linkProperties:saving manoeuvres:saving ' + linearAssetSavingEvents + ' ' + pointAssetSavingEvents, function() {
      indicatorOverlay();
    });

    var fetchedEventNames = _.map(multiElementEventNames, function(name) { return name + ':fetched'; }).join(' ');
    eventbus.on('asset:saved asset:fetched asset:created speedLimits:fetched linkProperties:available manoeuvres:fetched pointAssets:fetched ' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    var massUpdateFailedEventNames = _.map(multiElementEventNames, function(name) { return name + ':massUpdateFailed'; }).join(' ');
    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed ' + massUpdateFailedEventNames, function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('confirm:show', function() { new Confirm(); });
  };

  var createOpenLayersMap = function(startupParameters, layers) {
    var map = new ol.Map({
      target: 'mapdiv',
      layers: layers,
      view: new ol.View({
        center: [startupParameters.lon, startupParameters.lat],
        projection: 'EPSG:3067',
        zoom: startupParameters.zoom
      })
    });
    return map;
  };

  var setupMap = function(backend, models, withTileMaps, startupParameters) {
    var tileMaps = new TileMapCollection(map, "");

    var map = createOpenLayersMap(startupParameters, tileMaps.layers);

    // var NavigationControl = OpenLayers.Class(OpenLayers.Control.Navigation, {
    //   wheelDown: function(evt, delta) {
    //     if (applicationModel.canZoomOut()) {
    //       return OpenLayers.Control.Navigation.prototype.wheelDown.apply(this,arguments);
    //     } else {
    //       new Confirm();
    //     }
    //   }
    // });
    //
    // map.addControl(new NavigationControl());
    //
    var mapOverlay = new MapOverlay($('.container'));

    var roadLayer = new RoadLayer3(map, models.roadCollection);

    new LinkPropertyForm(models.selectedLinkProperty);

    var layers = _.merge({
      road: roadLayer,
      linkProperty: new LinkPropertyLayer(map, roadLayer, models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel)});

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new CoordinatesDisplay(map, mapPluginsContainer);

    // Show environment name next to Digiroad logo
    $('#notification').append(Environment.localizedName());

    // Show information modal in integration environment (remove when not needed any more)
    if (Environment.name() === 'integration') {
      showInformationModal('Huom!<br>Tämä sivu ei ole enää käytössä.<br>Digiroad-sovellus on siirtynyt osoitteeseen <a href="https://extranet.liikennevirasto.fi/digiroad/" style="color:#FFFFFF;text-decoration: underline">https://extranet.liikennevirasto.fi/digiroad/</a>');
    }

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(map.getView().getZoom(), map.getLayers().getArray()[0].getExtent());

    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  function getSelectedPointAsset(pointAssets, layerName) {
    return _(pointAssets).find({ layerName: layerName }).selectedPointAsset;
  }

  function groupAssets(linkPropertiesModel) {

    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);

    return [
      [roadLinkBox]
    ];

    function getLinearAsset(typeId) {
      var asset = _.find(linearAssets, {typeId: typeId});
      if (asset) {
        var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled];
        return [new LinearAssetBox(asset.selectedLinearAsset, asset.layerName, asset.title, asset.className, legendValues)];
      }
      return [];
    }

    function getPointAsset(typeId) {
      var asset = _.find(pointAssets, {typeId: typeId});
      if (asset) {
        return [PointAssetBox(asset.selectedPointAsset, asset.title, asset.layerName, asset.legendValues)];
      }
      return [];
    }
  }

  // Shows modal with message and close button
  function showInformationModal(message) {
    $('.container').append('<div class="modal-overlay confirm-modal" style="z-index: 2000"><div class="modal-dialog"><div class="content">' + message + '</div><div class="actions"><button class="btn btn-secondary close">Sulje</button></div></div></div></div>');
    $('.confirm-modal .close').on('click', function() {
      $('.confirm-modal').remove();
    });
  }

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
