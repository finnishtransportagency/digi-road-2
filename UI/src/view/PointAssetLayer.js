(function(root) {
  root.PointAssetLayer = function(params) {
    var roadLayer = params.roadLayer,
      collection = params.collection,
      map = params.map;

    Layer.call(this, 'pedestrianCrossing', roadLayer);
    var me = this;
    me.minZoomForContent = zoomlevels.minZoomForAssets;
    var assetLayer = new OpenLayers.Layer.Boxes('pedestrianCrossing');
    map.addLayer(assetLayer);

    this.refreshView = function() {
      collection.fetch(map.getExtent()).then(function(assets) {
        _.each(assets, function(asset) {
          var bounds = OpenLayers.Bounds.fromArray([asset.lon, asset.lat, asset.lon + 15, asset.lat + 15]);
          var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
          $(box.div)
            .css('overflow', 'visible !important')
            .css('background-image', 'url(./images/center-marker.svg)');
          assetLayer.addMarker(box);
        });
      });
    };

    this.activateSelection = function() {
    };
  };
})(this);