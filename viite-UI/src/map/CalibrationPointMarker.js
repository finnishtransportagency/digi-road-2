(function(root) {
  root.CalibrationPoint = function(data) {
    var cachedMarker = null;
    var cachedDirectionArrow = null;

    var defaultMarkerGraphics = {
      externalGraphic: 'images/calibration-point.png',
      graphicWidth: 16,
      graphicHeight: 30,
      graphicXOffset: -8,
      graphicYOffset: -30
    };

    var createCalibrationPointMarker = function() {
      var markerGraphics = _.clone(defaultMarkerGraphics);
//      markerGraphics.rotation = 90;
      var marker =  new ol.Feature({
          geometry: new ol.geom.Point([data.x, data.y]),
        }
      );


    var calibrationPointMarkerStyle = new ol.style.Style({
      image: new ol.style.Icon({
        src: "images/calibration-point.png",
        anchor: [0.5, 1]
      })
    });

    marker.setStyle(calibrationPointMarkerStyle);
    return marker;
    };

    var getMarker = function(shouldCreate) {
      if (shouldCreate || !cachedMarker) {
        cachedMarker = createCalibrationPointMarker();
      }
      return cachedMarker;
    };

    var getCalibrationPointMarker = function() {
      return cachedMarker;
    };

    var getDirectionArrow = function(shouldCreate) {
      if (shouldCreate || !cachedDirectionArrow) {
        cachedDirectionArrow = createCalibrationPointMarker();
      }
      return cachedDirectionArrow;
    };

    var moveTo = function(lonlat) {
      getDirectionArrow().move(lonlat);
      getCalibrationPointMarker().moveTo(lonlat);
    };

    var select = function() { getCalibrationPointMarker().select(); };

    var deselect = function() { getCalibrationPointMarker().deselect(); };

    var finalizeMove = function() {
      getCalibrationPointMarker().finalizeMove();
    };

    var rePlaceInGroup = function() { getCalibrationPointMarker().rePlaceInGroup(); };

    return {
      getMarker: getMarker,
      getDirectionArrow: getDirectionArrow,
      moveTo: moveTo,
      select: select,
      deselect: deselect,
      finalizeMove: finalizeMove,
      rePlaceInGroup: rePlaceInGroup
    };
  };
}(this));
